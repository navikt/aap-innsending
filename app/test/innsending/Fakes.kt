package innsending

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.nimbusds.jwt.JWTParser
import innsending.arkiv.ArkivResponse
import innsending.arkiv.Journalpost
import innsending.kafka.KafkaFake
import innsending.redis.Redis
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

class Fakes : AutoCloseable {
    val texas = embeddedServer(Netty, port = 0, module = Application::texas).apply { start() }
    val pdfGen = embeddedServer(Netty, port = 0, module = Application::pdfGen).apply { start() }
    val oppslag = embeddedServer(Netty, port = 0, module = Application::oppslag).apply { start() }
    val virusScan =
        embeddedServer(Netty, port = 0, module = Application::virusScan).apply { start() }
    val joark = JoarkFake()
    val redis = Redis(InitTestRedis.uri)
    val kafka = KafkaFake()

    init {
        // Texas
        System.setProperty("NAIS_TOKEN_ENDPOINT", "http://localhost:${texas.port()}/token")
        System.setProperty("NAIS_TOKEN_EXCHANGE_ENDPOINT", "http://localhost:${texas.port()}/token/exchange")
        System.setProperty("NAIS_TOKEN_INTROSPECTION_ENDPOINT", "http://localhost:${texas.port()}/introspect")
    }

    override fun close() {
        texas.stop(0L, 0L)
        pdfGen.stop(0L, 0L)
        oppslag.stop(0L, 0L)
        virusScan.stop(0L, 0L)
        joark.close()
        redis.close()
        kafka.close()
    }
}

class JoarkFake : AutoCloseable {
    private val server = create().apply { start() }
    val port = server.port()
    val receivedRequest = CompletableDeferred<Journalpost>()

    private fun create(): EmbeddedServer<*, *> =
        embeddedServer(Netty, port = 0, module = {
            install(ContentNegotiation) {
                jackson {
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    registerModule(JavaTimeModule())
                }
            }

            routing {
                post("/rest/journalpostapi/v1/journalpost") {
                    receivedRequest.complete(call.receive())
                    call.respond(HttpStatusCode.OK, ArkivResponse("1234", true, emptyList()))
                }
            }
        })

    override fun close() = server.stop(0, 0)
}

fun Application.texas() {
    install(ContentNegotiation) { jackson() }
    routing {
        post("/token") {
            TODO()
        }

        post("/token/exchange") {
            val token = TokenXGen().generate("1234567890")
            call.respond(Token(expires_in = 3600, access_token = token))
        }

        post("/introspect") {
            call.respond(mapOf("active" to true))
        }
    }
}

data class Token(val expires_in: Long, val access_token: String)

fun Application.pdfGen() {
    install(ContentNegotiation) { jackson() }
    routing {
        post("/api/v1/genpdf/image/aap-pdfgen") {
            val res = Resource.read("/resources/pdf/minimal.pdf")
            call.respond(res)
        }
        post("/api/v1/genpdf/aap-pdfgen/soknad") {
            val res = Resource.read("/resources/pdf/minimal.pdf")
            call.respond(res)
        }
    }
}

fun Application.oppslag() {
    install(ContentNegotiation) { jackson() }
    routing {
        get("/person/navn") {
            call.respondText(
                """{"fornavn": "Ola", "etternavn": "Nordmann"}""",
                ContentType.Application.Json
            )
        }
    }
}

fun Application.virusScan() {
    install(ContentNegotiation) { jackson() }
    routing {
        put("/scan") {
            call.respondText(
                """[{"Result": "OK"}]""",
                ContentType.Application.Json
            )
        }
    }
}

object Resource {
    fun read(path: String): ByteArray =
        requireNotNull(this::class.java.getResource(path)).readBytes()
}

class VirusFoundFake : AutoCloseable {
    private val server = embeddedServer(Netty, port = 0) {
        install(ContentNegotiation) { jackson() }
        routing {
            put("/scan") {
                call.respondText("""[{"Result": "FOUND"}]""", ContentType.Application.Json)
            }
        }
    }.apply { start() }

    fun port(): Int = server.port()

    override fun close() = server.stop(0L, 0L)
}

fun EmbeddedServer<*, *>.port(): Int =
    runBlocking { this@port.engine.resolvedConnectors() }
        .first { it.type == ConnectorType.HTTP }
        .port