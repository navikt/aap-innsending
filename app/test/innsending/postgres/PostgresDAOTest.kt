package innsending.postgres

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class PostgresDAOTest : PostgresTestBase() {
    @Test
    fun `Inserter en innsending`() {
        PostgresDAO.insertInnsending(
            UUID.randomUUID(),
            "12345678910",
            LocalDateTime.now(),
            "orginalSøknadJson".toByteArray(),
            "søknad".toByteArray(),
            dataSource.connection
        )

        assertEquals(1, countInnsending())
    }

    @Test
    fun `Inserter fil`() {
        val søknadId = UUID.randomUUID()
        dataSource.transaction {
            PostgresDAO.insertInnsending(
                søknadId,
                "12345678910",
                LocalDateTime.now(),
                "orginalSøknadJson".toByteArray(),
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
        dataSource.transaction {
            PostgresDAO.insertInnsending(
                søknadId,
                "12345678910",
                LocalDateTime.now(),
                "søknad".toByteArray(),
                "orginalSøknadJson".toByteArray(),
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

        PostgresDAO.deleteInnsending(søknadId, dataSource.connection)

        assertEquals(0, countInnsending())
        assertEquals(0, countFiler())
    }

    @Test
    fun `Henter en komplett innsending med filer`() {
        val søknadId = UUID.randomUUID()
        dataSource.transaction {
            PostgresDAO.insertInnsending(
                søknadId,
                "12345678910",
                LocalDateTime.now(),
                "orginalSøknadJson".toByteArray(),
                "søknad".toByteArray(),
                it
            )
            PostgresDAO.insertFil(søknadId, UUID.randomUUID(), "fil1".toByteArray(), "tittel1", it)
            PostgresDAO.insertFil(søknadId, UUID.randomUUID(), "fil2".toByteArray(), "tittel2", it)
            PostgresDAO.insertFil(søknadId, UUID.randomUUID(), "fil3".toByteArray(), "tittel3", it)
            PostgresDAO.insertFil(søknadId, UUID.randomUUID(), "fil4".toByteArray(), "tittel4", it)
            PostgresDAO.insertFil(søknadId, UUID.randomUUID(), "fil5".toByteArray(), "tittel5", it)
        }

        val innsending = PostgresDAO.selectInnsendingMedFiler(søknadId, dataSource.connection)

        assertEquals(søknadId, innsending?.id)
        assertEquals(5, innsending?.fil?.size)
    }

    @Test
    fun `insert logg ignorerer andre forsøk`() {
        val id = UUID.randomUUID()
        dataSource.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now(), "1234",id, "SOKNAD", it)
        }

        assertEquals(1, countLogg())

        dataSource.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now(), "1234", id, "SOKNAD", it)
        }

        assertEquals(1, countLogg())
    }

    @Test
    fun `hent fra logg sorterer riktig`() {
        dataSource.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now().minusDays(1), "1234", UUID.randomUUID(),"SOKNAD", it)
        }

        dataSource.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now(), "4321", UUID.randomUUID(),"SOKNAD", it)
        }

        val liste = dataSource.transaction {
            PostgresDAO.selectLogg("12345678910", InnsendingType.SOKNAD.name, it)
        }

        assertEquals(2, liste.size)
        assertEquals("4321", liste[0].journalpost)
    }

    @Test
    fun `hent fra logg filtrerer på SOKNAD`() {
        dataSource.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now().minusDays(1), "1234",UUID.randomUUID(), "SOKNAD", it)
        }

        dataSource.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now(), "4321", UUID.randomUUID(),"ETTERSENDING", it)
        }

        val liste = dataSource.transaction {
            PostgresDAO.selectLogg("12345678910", InnsendingType.SOKNAD.name, it)
        }

        assertEquals(1, liste.size)
    }

    @Test
    fun `henter ut ettersending`() {
        val innsendingId = UUID.randomUUID()
        val ettersendingId = UUID.randomUUID()

        dataSource.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now().minusDays(1), "1234", innsendingId, "SOKNAD", it)
        }

        dataSource.transaction {
            PostgresDAO.insertLogg("12345678910", LocalDateTime.now(), "4321", ettersendingId,"ETTERSENDING", it)
        }

        dataSource.transaction {
            PostgresDAO.insertSoknadEttersending(innsendingId, ettersendingId, it)
        }

        val res = dataSource.transaction {
            PostgresDAO.selectSoknadMedEttersendelser(innsendingId, it)
        }

        assertEquals(res?.ettersendinger?.first()?.innsendingsId, ettersendingId)
    }

}
