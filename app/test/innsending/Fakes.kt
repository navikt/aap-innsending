package innsending

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
        get("/.well-known/jwks.json") {
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
            install(ContentNegotiation) { jackson() }

            routing {
                post("/rest/journalpostapi/v1/journalpost") {
                    receivedRequest.complete(call.receive())
                    call.respond(HttpStatusCode.OK, "OK")
                }
            }
        })

    override fun close() = server.stop(0, 0)
}

fun Application.azure() {
    install(ContentNegotiation) { jackson() }
    routing {
        post("/token") {
            require(call.receiveText() == "client_id=test&client_secret=test&scope=fillagerScope&grant_type=client_credentials")
            call.respond(Token(
                expires_in = 3599,
                access_token = "very.secure.token"
            ))
        }
    }
}

data class Token(val expires_in: Long, val access_token: String)

fun Application.pdfGen() {
    install(ContentNegotiation) { jackson() }
    routing {
        post("/api/v1/genpdf/image/fillager") {
            val res = Resource.read("/resources/pdf/minimal.pdf")
            call.respond(res)
        }
        post("/api/v1/genpdf/aap-pdfgen/soknad") {
            val res = Resource.read("/resources/pdf/minimal.pdf")
            call.respond(res)
        }
    }
}

fun Application.virusScan() {
    install(ContentNegotiation) { jackson() }
    routing {
        put("/scan") {
            call.respondText(
                """[{"result": "OK"}]""",
                ContentType.Application.Json
            )
        }
    }
}

object Resource {
    fun read(path: String): ByteArray = requireNotNull(this::class.java.getResource(path)).readBytes()
}

fun NettyApplicationEngine.port() = runBlocking { resolvedConnectors() }.first { it.type == ConnectorType.HTTP }.port