package innsending.postgres

import innsending.routes.FilMetadata
import innsending.routes.Innsending
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class PostgresRepoTest : H2TestBase() {
    private val repo = PostgresRepo(h2)

    @Test
    fun `Insert en innsending med fil`() {
        val søknadId = UUID.randomUUID()
        val fil1 = FilMetadata(UUID.randomUUID().toString(), "Tittel1")
        val fil2 = FilMetadata(UUID.randomUUID().toString(), "Tittel2")
        val innsending = Innsending(
                kvittering = mapOf("søknad" to "søknad"),
                soknad = mapOf("søknad" to "søknad"),
            filer = listOf(fil1, fil2)
        )

        val filer = listOf(
            Pair(fil1, "fil1".toByteArray()),
            Pair(fil2, "fil2".toByteArray()),
        )

        repo.lagreInnsending(søknadId, "12345678910", LocalDateTime.now(), innsending, filer)

        assertEquals(1, countInnsending())
        assertEquals(2, countFiler())
    }

    @Test
    fun `Feil ruller tilbake alt`() {
        val søknadId = UUID.randomUUID()
        val fil1 = FilMetadata(UUID.randomUUID().toString(), "Tittel1")
        val fil2 = FilMetadata("Ikke en UUID :)", "Tittel2")
        val innsending = Innsending(
            kvittering = mapOf("søknad" to "søknad"),
            soknad = mapOf("søknad" to "søknad"),
            filer = listOf(fil1, fil2)
        )

        val filer = listOf(
            Pair(fil1, "fil1".toByteArray()),
            Pair(fil2, "fil2".toByteArray()),
        )

        try {
            repo.lagreInnsending(søknadId, "12345678910", LocalDateTime.now(), innsending, filer)
        } catch (ignore: Throwable) {
            // Feilen skal boble opp til toppen av ktor og returneres til frontend, her ignorer vi
        }

        assertEquals(0, countInnsending())
        assertEquals(0, countFiler())
    }
}
