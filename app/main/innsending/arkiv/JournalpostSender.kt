package innsending.arkiv

import innsending.postgres.InnsendingMedFiler
import innsending.postgres.PostgresRepo
import kotlinx.coroutines.runBlocking
import java.util.*

class JournalpostSender(
    private val client: JoarkClient,
    private val repo: PostgresRepo,
) {

    fun arkiverAltSomKanArkiveres(søknadSomPdf: ByteArray, innsending: InnsendingMedFiler) {
        val søknadDokument = lagSøknadDokument(søknadSomPdf)
        val vedleggDokumenter = lagVedleggDokumenter(innsending)

        val journalpost = Journalpost(
            tittel = "Søknad AAP",
            avsenderMottaker = Journalpost.AvsenderMottaker(
                id = Journalpost.Fødselsnummer(innsending.personident)
            ),
            bruker = Journalpost.Bruker(
                id = Journalpost.Fødselsnummer(innsending.personident)
            ),
            dokumenter = listOf(søknadDokument) + vedleggDokumenter,
            eksternReferanseId = innsending.id.toString(),
            datoMottatt = innsending.opprettet
        )

        val arkivResponse = client.opprettJournalpost(journalpost, innsending.id.toString())
        if (arkivResponse != null) {
            repo.loggførJournalføring(innsending.personident, innsending.opprettet, arkivResponse.journalpostId)
            repo.slettInnsending(innsending.id)
        }
    }

    private fun lagSøknadDokument(søknad: ByteArray): Journalpost.Dokument {
        val søknadSomPdf = runBlocking {
            Base64.getEncoder().encodeToString(søknad)
        }

        return Journalpost.Dokument(
            tittel = "Søknad",
            brevkode = "NAV 11-13.05",
            dokumentVarianter = listOf(Journalpost.DokumentVariant(fysiskDokument = søknadSomPdf))
        )
    }

    private fun lagVedleggDokumenter(innsending: InnsendingMedFiler): List<Journalpost.Dokument> {
        return innsending.vedlegg.map { vedlegg ->
            Journalpost.Dokument(
                tittel = vedlegg.tittel,
                brevkode = "NAV 11-13.05",
                dokumentVarianter = listOf(
                    Journalpost.DokumentVariant(fysiskDokument = Base64.getEncoder().encodeToString(vedlegg.data))
                )
            )
        }
    }
}
