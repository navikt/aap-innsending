package innsending.postgres

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class PostgresDAOTest : H2TestBase() {
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
    fun `Inserter fil`() {
        val søknadId = UUID.randomUUID()
        h2.transaction {
            PostgresDAO.insertInnsending(
                søknadId,
                "12345678910",
                LocalDateTime.now(),
                "søknad".toByteArray(),
                it
            )
            PostgresDAO.insertFil(
                søknadId,
                UUID.randomUUID(),
                "fil".toByteArray(),
                "tittel",
                it
            )
        }

        assertEquals(1, countFiler())
    }

    @Test
    fun `Sletting av innsending sletter også fil`() {
        val søknadId = UUID.randomUUID()
        h2.transaction {
            PostgresDAO.insertInnsending(
                søknadId,
                "12345678910",
                LocalDateTime.now(),
                "søknad".toByteArray(),
                it
            )
            PostgresDAO.insertFil(
                søknadId,
                UUID.randomUUID(),
                "fil".toByteArray(),
                "tittel",
                it
            )
        }

        assertEquals(1, countFiler())

        PostgresDAO.deleteInnsending(søknadId, h2.connection)

        assertEquals(0, countInnsending())
        assertEquals(0, countFiler())
    }

    @Test
    fun `Henter en komplett innsending med filer`() {
        val søknadId = UUID.randomUUID()
        h2.transaction {
            PostgresDAO.insertInnsending(
                søknadId,
                "12345678910",
                LocalDateTime.now(),
                "søknad".toByteArray(),
                it
            )
            PostgresDAO.insertFil(søknadId, UUID.randomUUID(), "fil1".toByteArray(), "tittel1", it)
            PostgresDAO.insertFil(søknadId, UUID.randomUUID(), "fil2".toByteArray(), "tittel2", it)
            PostgresDAO.insertFil(søknadId, UUID.randomUUID(), "fil3".toByteArray(), "tittel3", it)
            PostgresDAO.insertFil(søknadId, UUID.randomUUID(), "fil4".toByteArray(), "tittel4", it)
            PostgresDAO.insertFil(søknadId, UUID.randomUUID(), "fil5".toByteArray(), "tittel5", it)
        }

        val innsending = PostgresDAO.selectInnsendingMedFiler(søknadId, h2.connection)

        assertEquals(søknadId, innsending.id)
        assertEquals(5, innsending.fil.size)
    }

    @Test
    fun `insert logg ignorerer andre forsøk`() {
        h2.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now(), "1234", "SOKNAD", it)
        }

        assertEquals(1, countLogg())

        h2.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now(), "1234", "SOKNAD", it)
        }

        assertEquals(1, countLogg())
    }

    @Test
    fun `hent fra logg sorterer riktig`() {
        h2.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now().minusDays(1), "1234", "SOKNAD", it)
        }

        h2.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now(), "4321", "SOKNAD", it)
        }

        val liste = h2.transaction {
            PostgresDAO.selectLogg("12345678910", it)
        }

        assertEquals(2, liste.size)
        assertEquals("4321", liste[0].journalpost)
    }

    @Test
    fun `hent fra logg filtrerer på SOKNAD`() {
        h2.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now().minusDays(1), "1234", "SOKNAD", it)
        }

        h2.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now(), "4321", "ETTERSENDING", it)
        }

        val liste = h2.transaction {
            PostgresDAO.selectLogg("12345678910", it)
        }

        assertEquals(1, liste.size)
    }
}
