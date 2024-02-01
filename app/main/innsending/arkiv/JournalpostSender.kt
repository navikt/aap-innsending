package innsending.arkiv

import com.fasterxml.jackson.databind.ObjectMapper
import innsending.SECURE_LOGGER
import innsending.postgres.InnsendingMedFiler
import innsending.postgres.InnsendingType
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
        fun dokumenter(): List<Journalpost.Dokument> {
            val søknadDokument = lagSøknadDokument(søknadSomPdf)
            val vedleggDokumenter = lagDokumenter(innsending)
            val orginalSøknadDokument = innsending.søknad?.let {
                val encoded = Base64.getEncoder().encodeToString(it)
                val orginalSøknadJson = Journalpost.DokumentVariant("JSON", encoded, "ORIGINAL")
                Journalpost.Dokument(
                    tittel = "Orginal søknad json",
                    dokumentVarianter = listOf(orginalSøknadJson)
                )
            }
            return if (orginalSøknadDokument != null) {
                listOf(søknadDokument) + vedleggDokumenter + orginalSøknadDokument
            } else {
                listOf(søknadDokument) + vedleggDokumenter
            }
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
        SECURE_LOGGER.info("Opprettet journalpost {} for {}", arkivResponse.journalpostId, innsending.personident)
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

        logger.info("Lagrer ettersending for {}: {}", innsending.id, journalpost)
        val arkivResponse = client.opprettJournalpost(journalpost, innsending.id.toString())
        logger.info("Lagret {}", arkivResponse.journalpostId)
        repo.loggførJournalføring(
            personIdent = innsending.personident,
            mottattDato = innsending.opprettet,
            journalpostId = arkivResponse.journalpostId,
            innsendingsId = innsending.id,
            type = InnsendingType.ETTERSENDING
        )

        repo.slettInnsending(innsending.id)
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
