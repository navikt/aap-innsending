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
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import java.util.*

class Fakes : AutoCloseable {
    val azure = embeddedServer(Netty, port = 0, module = Application::azure).apply { start() }
    val tokenx = embeddedServer(Netty, port = 0, module = Application::tokenx).apply { start() }
    val joark = embeddedServer(Netty, port = 0, module = Application::joark).apply { start() }
    val pdfGen = embeddedServer(Netty, port = 0, module = Application::pdfGen).apply { start() }
    val virusScan = embeddedServer(Netty, port = 0, module = Application::virusScan).apply { start() }

    override fun close() {
        azure.stop(0L, 0L)
        tokenx.stop(0L, 0L)
        joark.stop(0L, 0L)
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

fun Application.joark() {
    install(ContentNegotiation) { jackson() }
    routing {
        post("/rest/journalpostapi/v1/journalpost") {
            val actual = call.receive<Journalpost>()
            val expected = Journalpost(
                tittel = "Søknad om AAP",
                avsenderMottaker = Journalpost.AvsenderMottaker(
                    id = Journalpost.Fødselsnummer("12345678910"),
                    navn = "Kari Nordmann"
                ),
                bruker = Journalpost.Bruker(
                    id = Journalpost.Fødselsnummer("12345678910")
                ),
                dokumenter = listOf(
                    Journalpost.Dokument(
                        tittel = "Søknad om AAP",
                        dokumentVarianter = listOf(
                            Journalpost.DokumentVariant(
                                fysiskDokument = Base64.getEncoder().encodeToString(
                                    Resource.read(
                                        "/resources/pdf/minimal.pdf"
                                    )
                                ),
                            )
                        )
                    )
                ),
                eksternReferanseId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString(),
                kanal = "NAV_NO",
                journalposttype = "INNGAAENDE",
                tilleggsopplysninger = listOf(
                    Journalpost.Tilleggsopplysning(
                        nokkel = "versjon",
                        verdi = "1.0"
                    )
                ),
                tema = "AAP"
            )

            assertEquals(expected, actual)

            call.respond(HttpStatusCode.OK, "OK")
        }

    }
}

fun Application.azure() {
    install(ContentNegotiation) { jackson() }
    routing {
        post("/token") {
            require(call.receiveText() == "client_id=test&client_secret=test&scope=fillagerScope&grant_type=client_credentials")
            call.respondText(
                """{
                    "token_type": "Bearer",
                    "expires_in": 3599,
                    "access_token": "very.secure.token" 
                }"""
            )
        }
    }
}

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