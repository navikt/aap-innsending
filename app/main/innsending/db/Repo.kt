package innsending.db

import innsending.domene.Innsending
import innsending.domene.NyInnsendingRequest
import java.util.*
import javax.sql.DataSource

class Repo(dataSource: DataSource) {
    private val filDAO = FilDAO(dataSource)
    private val innsendingDAO = InnsendingDAO(dataSource)

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

    fun hentSøknad(innsendingsreferanse: UUID):String{
        val innsending:Innsending=hentInnsending(innsendingsreferanse)
        return innsending.data
    }

    fun hentInnsending(innsendingsreferanse: UUID):Innsending{
        return innsendingDAO.getInnsending(innsendingsreferanse)
    }

    fun hentInnsendingMedBrukerId(brukerId: String):Innsending{
        return innsendingDAO.getInnsendingByBrukerId(brukerId)
    }

    fun opprettNyInnsending(innsendingsreferanse: UUID, brukerId: String, brevkode:String?){
        innsendingDAO.insertInnsending(innsendingsreferanse, brukerId, brevkode)
    }

    fun oppdaterInnsending(innsendingsreferanse: UUID,innsending: NyInnsendingRequest){
        innsendingDAO.updateInnsending(innsendingsreferanse, innsending)
    }

    fun slettInnsending(innsendingsreferanse: UUID){
        innsendingDAO.deleteInnsending(innsendingsreferanse)
    }



}