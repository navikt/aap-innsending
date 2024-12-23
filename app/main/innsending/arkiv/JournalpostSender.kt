package innsending.arkiv

import innsending.logger
import innsending.postgres.InnsendingMedFiler
import innsending.postgres.InnsendingType
import innsending.postgres.PostgresRepo
import kotlinx.coroutines.runBlocking
import java.util.*

class JournalpostSender(
    private val client: JoarkClient,
    private val repo: PostgresRepo,
) {
    fun arkiverSøknad(søknadSomPdf: ByteArray, innsending: InnsendingMedFiler) {
        fun dokumenter(): List<Journalpost.Dokument> {
            val orginalSøknadDokument = innsending.søknad?.let {
                val encoded = Base64.getEncoder().encodeToString(it)
                Journalpost.DokumentVariant("JSON", encoded, "ORIGINAL")
            }
            val søknadDokument = lagSøknadDokument(søknadSomPdf, orginalSøknadDokument)
            val vedleggDokumenter = lagDokumenter(innsending)
            return listOf(søknadDokument) + vedleggDokumenter
        }

        val journalpost = Journalpost(
            tittel = "Søknad AAP",
            avsenderMottaker = Journalpost.AvsenderMottaker(
                id = Journalpost.Fødselsnummer(innsending.personident)
            ),
            bruker = Journalpost.Bruker(
                id = Journalpost.Fødselsnummer(innsending.personident)
            ),
            dokumenter = dokumenter(),
            eksternReferanseId = innsending.id.toString(),
            datoMottatt = innsending.opprettet
        )

        val arkivResponse = client.opprettJournalpost(journalpost, innsending.id.toString())
        logger.info("Opprettet journalpost {} for eksternreferanseID {}", arkivResponse.journalpostId, journalpost.eksternReferanseId)

        repo.loggførJournalføring(
            personIdent = innsending.personident,
            mottattDato = innsending.opprettet,
            journalpostId = arkivResponse.journalpostId,
            innsendingsId = innsending.id,
            type = InnsendingType.SOKNAD
        )

        repo.slettInnsending(innsending.id)
    }

    fun arkiverEttersending(innsending: InnsendingMedFiler) {
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
            eksternReferanseId = innsending.id.toString(),
            datoMottatt = innsending.opprettet
        )

        val arkivResponse = client.opprettJournalpost(journalpost, innsending.id.toString())
        logger.info("Opprettet ettersending-journalpost {} for eksternreferanseID {}", arkivResponse.journalpostId, journalpost.eksternReferanseId)

        repo.loggførJournalføring(
            personIdent = innsending.personident,
            mottattDato = innsending.opprettet,
            journalpostId = arkivResponse.journalpostId,
            innsendingsId = innsending.id,
            type = InnsendingType.ETTERSENDING
        )

        repo.slettInnsending(innsending.id)
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

    private fun lagDokumenter(innsending: InnsendingMedFiler): List<Journalpost.Dokument> {
        return innsending.fil.map { fil ->
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
