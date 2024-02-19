package innsending

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import innsending.arkiv.ArkivResponse
import innsending.arkiv.Journalpost
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
    val azure = embeddedServer(Netty, port = 0, module = Application::azure).apply { start() }
    val tokenx = embeddedServer(Netty, port = 0, module = Application::tokenx).apply { start() }
    val joark = JoarkFake()
    val pdfGen = embeddedServer(Netty, port = 0, module = Application::pdfGen).apply { start() }
    val oppslag = embeddedServer(Netty, port = 0, module = Application::oppslag).apply { start() }
    val virusScan = embeddedServer(Netty, port = 0, module = Application::virusScan).apply { start() }

    override fun close() {
        azure.stop(0L, 0L)
        tokenx.stop(0L, 0L)
        joark.close()
        pdfGen.stop(0L, 0L)
        virusScan.stop(0L, 0L)
    }
}

fun Application.tokenx() {
    install(ContentNegotiation) { jackson() }
    routing {
        get("/jwks") {
            call.respondText(TOKEN_X_JWKS)
        }
    }
}

class JoarkFake : AutoCloseable {
    private val server = create().apply { start() }
    val port = server.port()
    val receivedRequest = CompletableDeferred<Journalpost>()

    private fun create(): NettyApplicationEngine =
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

fun Application.azure() {
    install(ContentNegotiation) { jackson() }
    routing {
        post("/token") {
            val scopes = listOf(
                "api://dev-fss.teamdokumenthandtering.dokarkiv/.default",
                "api://dev-gcp.aap.oppslag/.default"
            )

            val request = call.receiveText()

            require(
                scopes
                    .map { "client_id=test&client_secret=test&scope=$it&grant_type=client_credentials" }
                    .any { it == request }
            ) {
                "Ukjent token request $request"
            }

            call.respond(
                Token(
                    expires_in = 3599,
                    access_token = "very.secure.token"
                )
            )
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
    fun read(path: String): ByteArray = requireNotNull(this::class.java.getResource(path)).readBytes()
}

fun NettyApplicationEngine.port() = runBlocking { resolvedConnectors() }.first { it.type == ConnectorType.HTTP }.port