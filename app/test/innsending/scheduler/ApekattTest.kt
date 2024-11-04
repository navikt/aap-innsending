package innsending.scheduler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import innsending.Fakes
import innsending.Resource
import innsending.TestConfig
import innsending.arkiv.Journalpost
import innsending.postgres.PostgresDAO
import innsending.postgres.PostgresTestBase
import innsending.postgres.toByteArray
import innsending.postgres.transaction
import innsending.server
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class ApekattTest : PostgresTestBase() {

    private fun testEmbedded(block: suspend (Fakes, HttpClient) -> Unit) {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)

            val app = TestApplication {
                application {
                    server(config, fakes.redis, dataSource, fakes.kafka)
                }
            }

            val client = app.createClient {
                install(ContentNegotiation) {
                    jackson {
                        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        registerModule(JavaTimeModule())
                    }
                }
            }

            runBlocking { app.start() }
            suspend {
                block(fakes, client)
            }
            app.stop()
        }
    }

    @Test
    fun `journalfører søknad`() = testEmbedded { fakes, _ ->
        dataSource.transaction {
            PostgresDAO.insertInnsending(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "12345678910",
                now,
                mapOf("søker" to "Søker").toByteArray(),
                mapOf("søker" to "Søker").toByteArray(),
                it
            )
        }

        val actual = withTimeout(2000) {
            fakes.joark.receivedRequest.await()
        }

        assertTrue(expectedSøknad.datoMottatt.isAfter(now.minusSeconds(1)))
        assertEquals(expectedSøknad, actual.copy(datoMottatt = now))

        assertTrue(fakes.kafka.hasProduced("12345678910"))

        assertEquals(0, countInnsending())
        assertEquals(0, countFiler())
        assertEquals(1, countLogg())
    }

    @Test
    fun `journalfører ettersendelse `() = testEmbedded { fakes, _ ->

        dataSource.transaction {
            PostgresDAO.insertInnsending(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "12345678910",
                now,
                mapOf("søker" to "Søker").toByteArray(),
                null,
                it
            )

            PostgresDAO.insertFil(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
                "dføalskdfjøalknser vf".toByteArray(),
                "Annet",
                it
            )
        }

        val actual = runBlocking {
            withTimeout(2000) {
                fakes.joark.receivedRequest.await()
            }
        }

        // LocalDateTime.now() blir kalt i InnsendingRoute.
        // Den vil derfor bli kalt noen tusendeler av et sekund før testen setter expected.datoMottatt=now
        // Worst case vil testen time ut etter 1 sec, derfor støtter vi opptil en 1sec gammel LocalDateTime.now()
        assertTrue(expectedEttersending.datoMottatt.isAfter(now.minusSeconds(1)))

        // Vi bytter actual sin datoMottat med testen sin 'now' for å kunne asserte på hele objektet
        assertEquals(expectedEttersending, actual.copy(datoMottatt = now))

        assertEquals(0, countInnsending())
        assertEquals(0, countFiler())
        assertEquals(1, countLogg())
    }

    private val now: LocalDateTime by lazy { LocalDateTime.now() }

    private val expectedSøknad = Journalpost(
        tittel = "Søknad AAP",
        avsenderMottaker = Journalpost.AvsenderMottaker(
            id = Journalpost.Fødselsnummer("12345678910")
        ),
        bruker = Journalpost.Bruker(id = Journalpost.Fødselsnummer("12345678910")),
        dokumenter = listOf(
            Journalpost.Dokument(
                tittel = "Søknad om Arbeidsavklaringspenger",
                brevkode = "NAV 11-13.05",
                dokumentVarianter = listOf(
                    Journalpost.DokumentVariant(
                        filtype = "PDFA",
                        fysiskDokument = Base64.getEncoder().encodeToString(
                            Resource.read("/resources/pdf/minimal.pdf")
                        ),
                        variantformat = "ARKIV"
                    ),
                    Journalpost.DokumentVariant(
                        filtype = "JSON",
                        fysiskDokument = Base64.getEncoder().encodeToString(
                            mapOf("søker" to "Søker").toByteArray()
                        ),
                        variantformat = "ORIGINAL"
                    )
                )
            )
        ),
        eksternReferanseId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString(),
        datoMottatt = now,
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
    private val expectedEttersending = Journalpost(
        tittel = "Ettersendelse til søknad om arbeidsavklaringspenger",
        avsenderMottaker = Journalpost.AvsenderMottaker(
            id = Journalpost.Fødselsnummer("12345678910")
        ),
        bruker = Journalpost.Bruker(id = Journalpost.Fødselsnummer("12345678910")),
        dokumenter = listOf(
            Journalpost.Dokument(
                tittel = "Annet",
                brevkode = "",
                dokumentVarianter = listOf(
                    Journalpost.DokumentVariant(
                        fysiskDokument = Base64.getEncoder().encodeToString(
                            "dføalskdfjøalknser vf".toByteArray()

                        ),
                    )
                )
            )
        ),
        eksternReferanseId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString(),
        datoMottatt = now,
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
}

