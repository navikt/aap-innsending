package innsending.db

import java.util.*
import javax.sql.DataSource

class Repo(dataSource: DataSource) {
    private val filDAO = FilDAO(dataSource)
    private val innsendingDAO = InnsendingDAO(dataSource)
    private val soknadDAO = SoknadDAO(dataSource)

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

    fun opprettNyInnsending(innsendingsreferanse: UUID, brukerId: String, brevkode:String){
        innsendingDAO.insertInnsending(innsendingsreferanse, brukerId, brevkode)
    }

    fun opdaterInnsending(innsendingsreferanse: UUID){
        innsendingDAO.updateInnsending(innsendingsreferanse)
    }

    fun slettInnsending(innsendingsreferanse: UUID){
        innsendingDAO.deleteInnsending(innsendingsreferanse)
    }

    fun opprettNySøknad(søknadId:UUID, innsendingsreferanse: UUID, brukerId: String, version:Int, data: String){
        soknadDAO.insertSoknad(søknadId, innsendingsreferanse, brukerId, version, data)
    }

    fun opdaterSøknad(søknadId: UUID, data:String){
        soknadDAO.updateSoknad(søknadId, data)
    }

    fun fullførSøknad(søknadId: UUID){
        soknadDAO.fullforSoknad(søknadId)
    }

    fun slettSøknad(søknadId: UUID){
        soknadDAO.deleteSoknad(søknadId)
    }


}