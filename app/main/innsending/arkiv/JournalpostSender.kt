package innsending.arkiv

import innsending.SECURE_LOGGER
import innsending.postgres.InnsendingMedFiler
import innsending.postgres.PostgresRepo
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("JournalpostSender")

class JournalpostSender(
    private val client: JoarkClient,
    private val repo: PostgresRepo,
) {

    fun arkiverSøknad(søknadSomPdf: ByteArray, innsending: InnsendingMedFiler) {
        val søknadDokument = lagSøknadDokument(søknadSomPdf)
        val vedleggDokumenter = lagDokumenter(innsending)

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
            SECURE_LOGGER.info("Opprettet journalpost {} for {}", arkivResponse.journalpostId, innsending.personident)
            repo.loggførJournalføring(innsending.personident, innsending.opprettet, arkivResponse.journalpostId)
            repo.slettInnsending(innsending.id)
        }
    }

    fun arkiverEttersending(innsending: InnsendingMedFiler){
        val vedleggDokumenter = lagDokumenter(innsending)

        val journalpost = Journalpost(
            tittel = "Ettersending AAP",
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

        logger.info("Lagrer ettersending for {}: {}", innsending.id, journalpost)
        val arkivResponse = client.opprettJournalpost(journalpost, innsending.id.toString())
        logger.info("Lagret {}", arkivResponse?.journalpostId)
        if (arkivResponse != null) {
            repo.loggførJournalføring(innsending.personident, innsending.opprettet, arkivResponse.journalpostId)
            repo.slettInnsending(innsending.id)
        }
        logger.info("Ettersendt {}", innsending.id)
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
