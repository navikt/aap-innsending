package innsending.jobb.arkivering

import innsending.arkiv.ArkivResponse
import innsending.arkiv.Journalpost
import innsending.db.InnsendingNy
import innsending.logger
import innsending.pdf.PdfGenClient
import innsending.postgres.InnsendingType
import java.util.Base64

class ArkiveringService(
    val joarkClient: JoarkClient,
    val pdfGen: PdfGenClient
) {
    fun arkiverSøknadInnsending(innsending: InnsendingNy): ArkivResponse {
        require(innsending.type == InnsendingType.SOKNAD)

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
            eksternReferanseId = innsending.eksternRef.toString(),
            datoMottatt = innsending.opprettet
        )

        val arkivResponse = joarkClient.opprettJournalpost(journalpost)
        logger.info(
            "Opprettet søknad-journalpost {} for eksternreferanseID {}",
            arkivResponse.journalpostId,
            journalpost.eksternReferanseId
        )
        return arkivResponse
    }

    fun dokumenter(innsending: InnsendingNy, soknadSomPdf: ByteArray): List<Journalpost.Dokument> {
        val orginalSøknadDokument = innsending.soknad?.let {
            val encoded = Base64.getEncoder().encodeToString(it)
            Journalpost.DokumentVariant("JSON", encoded, "ORIGINAL")
        }
        val søknadDokument = lagSøknadDokument(soknadSomPdf, orginalSøknadDokument)
        val vedleggDokumenter = lagDokumenter(innsending)
        return listOf(søknadDokument) + vedleggDokumenter
    }

    private fun lagSøknadDokument(søknad: ByteArray, original: Journalpost.DokumentVariant?): Journalpost.Dokument {
        return Journalpost.Dokument(
            tittel = "Søknad om Arbeidsavklaringspenger",
            brevkode = "NAV 11-13.05",
            dokumentVarianter = listOfNotNull(
                Journalpost.DokumentVariant(fysiskDokument = Base64.getEncoder().encodeToString(søknad)),
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

    fun arkiverEttersendelseInnsending(innsending: InnsendingNy): ArkivResponse {
        val vedleggDokumenter = lagDokumenter(innsending)

        val journalpost = Journalpost(
            tittel = "Ettersendelse til søknad om arbeidsavklaringspenger",
            avsenderMottaker = Journalpost.AvsenderMottaker(
                id = Journalpost.Fødselsnummer(innsending.personident)
            ),
            bruker = Journalpost.Bruker(
                id = Journalpost.Fødselsnummer(innsending.personident)
            ),
            dokumenter = vedleggDokumenter,
            eksternReferanseId = innsending.eksternRef.toString(),
            datoMottatt = innsending.opprettet
        )

        val arkivResponse = joarkClient.opprettJournalpost(journalpost)
        logger.info(
            "Opprettet ettersending-journalpost {} for eksternreferanseID {}",
            arkivResponse.journalpostId,
            journalpost.eksternReferanseId
        )
        return arkivResponse
    }
}
