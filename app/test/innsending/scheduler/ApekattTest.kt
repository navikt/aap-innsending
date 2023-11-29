package innsending.scheduler

import innsending.Fakes
import innsending.Resource
import innsending.TestConfig
import innsending.arkiv.Journalpost
import innsending.postgres.H2TestBase
import innsending.postgres.PostgresDAO
import innsending.postgres.transaction
import innsending.redis.JedisRedisFake
import innsending.server
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class ApekattTest : H2TestBase() {

    @Test
    fun `sjekker at apekatten sender journalpost og rydder opp`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)

            h2.transaction {
                PostgresDAO.insertInnsending(
                    UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                    "12345678910",
                    now,
                    Resource.read("/resources/images/bilde.jpg"),
                    it
                )
            }


            testApplication {
                application { server(config, jedis, h2) }
            }

            val actual = runBlocking {
                withTimeout(10000) {
                    fakes.joark.receivedRequest.await()
                }
            }

            // LocalDateTime.now() blir kalt i InnsendingRoute.
            // Den vil derfor bli kalt noen tusendeler av et sekund før testen setter expected.datoMottatt=now
            // Worst case vil testen time ut etter 10 sec, derfor støtter vi opptil en 10sec gammel LocalDateTime.now()
            assertTrue(expected.datoMottatt.isAfter(now.minusSeconds(10)))

            // Vi bytter actual sin datoMottat med testen sin 'now' for å kunne asserte på hele objektet
            assertEquals(expected, actual.copy(datoMottatt = now))

            assertEquals(0, countInnsending())
            assertEquals(0, countVedlegg())
            assertEquals(1, countLogg())
        }
    }

    private val now: LocalDateTime by lazy { LocalDateTime.now() }

    private val expected = Journalpost(
        tittel = "Søknad AAP",
        avsenderMottaker = Journalpost.AvsenderMottaker(
            id = Journalpost.Fødselsnummer("12345678910")
        ),
        bruker = Journalpost.Bruker(id = Journalpost.Fødselsnummer("12345678910")),
        dokumenter = listOf(
            Journalpost.Dokument(
                tittel = "Søknad",
                brevkode = "NAV 11-13.05",
                dokumentVarianter = listOf(
                    Journalpost.DokumentVariant(
                        fysiskDokument = Base64.getEncoder().encodeToString(
                            Resource.read("/resources/pdf/minimal.pdf")
                        ),
                    )
                )
            )
        ),
        eksternReferanseId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString(),
        datoMottatt = now,
        kanal = "NAV_NO",
        journalposttype = "INNGAAENDE",
        tilleggsopplysninger = listOf(Journalpost.Tilleggsopplysning(nokkel = "versjon", verdi = "1.0")),
        tema = "AAP"
    )
}
