package innsending.jobb.arkivering

import innsending.arkiv.ArkivResponse
import innsending.arkiv.Journalpost
import innsending.db.InnsendingNy
import innsending.db.InnsendingRepo
import innsending.pdf.PdfGenClient
import kotlinx.coroutines.runBlocking
import java.util.*

class ArkiveringService(
    val innsendingRepo: InnsendingRepo,
    val joarkClient: JoarkClient,
    val pdfGen: PdfGenClient
) {
    fun arkiverInnsending(innsendingsId: Long):ArkivResponse {
        val innsending = innsendingRepo.hent(innsendingsId)
        val pdf = pdfGen.søknadTilPdf(innsending)


        val journalpost = Journalpost(
            tittel = "Søknad AAP",
            avsenderMottaker = Journalpost.AvsenderMottaker(
                id = Journalpost.Fødselsnummer(innsending.personident)
            ),
            bruker = Journalpost.Bruker(
                id = Journalpost.Fødselsnummer(innsending.personident)
            ),
            dokumenter = dokumenter(innsending, pdf),
            eksternReferanseId = innsending.id.toString(),
            datoMottatt = innsending.opprettet
        )

        return joarkClient.opprettJournalpost(journalpost)
    }

    fun dokumenter(innsending:InnsendingNy, soknadSomPdf:ByteArray): List<Journalpost.Dokument> {
        val orginalSøknadDokument = innsending.soknad?.let {
            val encoded = Base64.getEncoder().encodeToString(it)
            Journalpost.DokumentVariant("JSON", encoded, "ORIGINAL")
        }
        val søknadDokument = lagSøknadDokument(soknadSomPdf, orginalSøknadDokument)
        val vedleggDokumenter = lagDokumenter(innsending)
        return listOf(søknadDokument) + vedleggDokumenter
    }

    private fun lagSøknadDokument(søknad: ByteArray, original: Journalpost.DokumentVariant?): Journalpost.Dokument {
        val søknadSomPdf = runBlocking {
            Base64.getEncoder().encodeToString(søknad)
        }

        return Journalpost.Dokument(
            tittel = "Søknad om Arbeidsavklaringspenger",
            brevkode = "NAV 11-13.05",
            dokumentVarianter = listOfNotNull(
                Journalpost.DokumentVariant(fysiskDokument = søknadSomPdf),
                original
            )
        )
    }

    private fun lagDokumenter(innsending: InnsendingNy): List<Journalpost.Dokument> {
        return innsending.filer.map { fil ->
            Journalpost.Dokument(
                tittel = fil.tittel,
                brevkode = "",
                dokumentVarianter = listOf(
                    Journalpost.DokumentVariant(fysiskDokument = Base64.getEncoder().encodeToString(fil.data))
                )
            )
        }
    }
}