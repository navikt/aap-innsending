package innsending.postgres

import innsending.postgres.InitTestDatabase.dataSource
import innsending.routes.Innsending
import innsending.routes.Vedlegg
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

internal class PostgresRepoTest : DatabaseTestBase() {

    private val repo = PostgresRepo(dataSource)

    @Test
    fun `Insert en innsending med vedlegg`() {
        val søknadId = UUID.randomUUID()
        val vedlegg1 = Vedlegg(UUID.randomUUID().toString(), "Tittel1")
        val vedlegg2 = Vedlegg(UUID.randomUUID().toString(), "Tittel2")
        val innsending = Innsending(
            soknad = "søknad".toByteArray(),
            vedlegg = listOf(vedlegg1, vedlegg2)
        )

        val vedleggListe = listOf(
            Pair(vedlegg1, "vedlegg1".toByteArray()),
            Pair(vedlegg2, "vedlegg2".toByteArray()),
        )

        repo.lagreSøknadMedVedlegg(søknadId, "12345678910", innsending, vedleggListe)

        assertEquals(1, countInnsending())
        assertEquals(2, countVedlegg())
    }

    @Test
    fun `Feil ruller tilbake alt`() {
        val søknadId = UUID.randomUUID()
        val vedlegg1 = Vedlegg(UUID.randomUUID().toString(), "Tittel1")
        val vedlegg2 = Vedlegg("Ikke en UUID :)", "Tittel2")
        val innsending = Innsending(
            soknad = "søknad".toByteArray(),
            vedlegg = listOf(vedlegg1, vedlegg2)
        )

        val vedleggListe = listOf(
            Pair(vedlegg1, "vedlegg1".toByteArray()),
            Pair(vedlegg2, "vedlegg2".toByteArray()),
        )

        try {
            repo.lagreSøknadMedVedlegg(søknadId, "12345678910", innsending, vedleggListe)
        } catch (ignore: Throwable) {
            // Feilen skal boble opp til toppen av ktor og returneres til frontend, her ignorer vi
        }

        assertEquals(0, countInnsending())
        assertEquals(0, countVedlegg())
    }

}