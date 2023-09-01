package innsending.db

import java.util.*
import javax.sql.DataSource

class Repo(dataSource: DataSource) {
    private val filDAO = FilDAO(dataSource)

    fun opprettNyFil(innsendingsreferanse: UUID, tittel: String?): UUID {
        val filreferanse = UUID.randomUUID()
        filDAO.insertFil(filreferanse, innsendingsreferanse, tittel)
        return filreferanse
    }

    fun oppdaterTittelPåFil(filReferanse: UUID, tittel: String) {
        filDAO.updateFil(filReferanse, tittel)
    }

    fun slettFil(filReferanse: UUID) {
        filDAO.deleteFil(filReferanse)
    }

}