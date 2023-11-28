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
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals


class ArkivSchedulerTest : H2TestBase() {

    @Test
    fun `should send journalpost`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)

            h2.transaction {
                PostgresDAO.insertInnsending(
                    UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                    "12345678910",
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
            assertEquals(expected, actual)
        }
    }

    private val expected = Journalpost(
        tittel = "Søknad om AAP",
        avsenderMottaker = Journalpost.AvsenderMottaker(
            id = Journalpost.Fødselsnummer("12345678910"),
            navn = "Kari Nordmann"
        ),
        bruker = Journalpost.Bruker(id = Journalpost.Fødselsnummer("12345678910")),
        dokumenter = listOf(
            Journalpost.Dokument(
                tittel = "Søknad om AAP",
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
        kanal = "NAV_NO",
        journalposttype = "INNGAAENDE",
        tilleggsopplysninger = listOf(Journalpost.Tilleggsopplysning(nokkel = "versjon", verdi = "1.0")),
        tema = "AAP"
    )
}