package innsending.postgres

import innsending.postgres.InitH2.dataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class InnsendingDAOTest : H2TestBase() {
    private val dao = InnsendingDAO(dataSource)

    @Test
    fun `Inserter en innsending`() {
        val søknadId = UUID.randomUUID()
        dao.insertInnsending(søknadId, "12345678910", "søknad".toByteArray())

        assertEquals(1, countInnsending())
    }

    @Test
    fun `Inserter vedlegg`() {
        val søknadId = UUID.randomUUID()
        dao.insertInnsending(søknadId, "12345678910", "søknad".toByteArray())

        dao.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg".toByteArray(), "tittel")

        assertEquals(1, countVedlegg())
    }

    @Test
    fun `Sletting av innsending sletter også vedlegg`() {
        val søknadId = UUID.randomUUID()
        dao.insertInnsending(søknadId, "12345678910", "søknad".toByteArray())

        dao.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg".toByteArray(), "tittel")

        assertEquals(1, countVedlegg())

        dao.deleteInnsending(søknadId)

        assertEquals(0, countInnsending())
        assertEquals(0, countVedlegg())
    }

    @Test
    fun `Henter en komplett innsending med vedlegg`() {
        val søknadId = UUID.randomUUID()
        dao.insertInnsending(søknadId, "12345678910", "søknad".toByteArray())

        dao.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg1".toByteArray(), "tittel1")
        dao.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg2".toByteArray(), "tittel2")
        dao.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg3".toByteArray(), "tittel3")
        dao.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg4".toByteArray(), "tittel4")
        dao.insertVedlegg(søknadId, UUID.randomUUID(), "vedlegg5".toByteArray(), "tittel5")

        val innsending = dao.selectInnsendingMedVedlegg(søknadId)

        assertEquals(søknadId, innsending.id)
        assertEquals(5, innsending.vedlegg.size)
    }
}
