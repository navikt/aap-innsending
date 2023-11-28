package innsending.postgres

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class InnsendingDAOTest : H2TestBase() {
    @Test
    fun `Inserter en innsending`() {
        PostgresDAO.insertInnsending(
            UUID.randomUUID(),
            "12345678910",
            LocalDateTime.now(),
            "søknad".toByteArray(),
            h2.connection
        )

        assertEquals(1, countInnsending())
    }

    @Test
    fun `Inserter vedlegg`() {
        val søknadId = UUID.randomUUID()
        h2.transaction {
            PostgresDAO.insertInnsending(
                søknadId,
                "12345678910",
                LocalDateTime.now(),
                "søknad".toByteArray(),
                it
            )
            PostgresDAO.insertVedlegg(
                søknadId,
                UUID.randomUUID(),
                "vedlegg".toByteArray(),
                "tittel",
                it
            )
        }

        assertEquals(1, countVedlegg())
    }

    @Test
    fun `Sletting av innsending sletter også vedlegg`() {
        val søknadId = UUID.randomUUID()
        h2.transaction {
            PostgresDAO.insertInnsending(
                søknadId,
                "12345678910",
                LocalDateTime.now(),
                "søknad".toByteArray(),
                it
            )
            PostgresDAO.insertVedlegg(
                søknadId,
                UUID.randomUUID(),
                "vedlegg".toByteArray(),
                "tittel",
                it
            )
        }

        assertEquals(1, countVedlegg())

        PostgresDAO.deleteInnsending(søknadId, h2.connection)

        assertEquals(0, countInnsending())
        assertEquals(0, countVedlegg())
    }

    @Test
    fun `Henter en komplett innsending med vedlegg`() {
        val søknadId = UUID.randomUUID()
        h2.transaction {
            PostgresDAO.insertInnsending(
                søknadId,
                "12345678910",
                LocalDateTime.now(),
                "søknad".toByteArray(),
                it
            )
            PostgresDAO.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg1".toByteArray(), "tittel1", it)
            PostgresDAO.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg2".toByteArray(), "tittel2", it)
            PostgresDAO.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg3".toByteArray(), "tittel3", it)
            PostgresDAO.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg4".toByteArray(), "tittel4", it)
            PostgresDAO.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg5".toByteArray(), "tittel5", it)
        }

        val innsending = PostgresDAO.selectInnsendingMedVedlegg(søknadId, h2.connection)

        assertEquals(søknadId, innsending.id)
        assertEquals(5, innsending.vedlegg.size)
    }
}
