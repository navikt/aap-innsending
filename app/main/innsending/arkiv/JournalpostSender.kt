package innsending.arkiv

import innsending.db.FilNy
import innsending.db.InnsendingNy
import innsending.db.InnsendingRepo
import innsending.logger
import innsending.postgres.InnsendingMedFiler
import innsending.postgres.InnsendingType
import innsending.postgres.PostgresRepo
import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.dbconnect.transaction
import java.util.*
import javax.sql.DataSource

class JournalpostSender(
    private val client: JoarkClient,
    private val repo: PostgresRepo,
    private val dataSource: DataSource
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

        dataSource.transaction { con ->
            InnsendingRepo(con).lagre(
                InnsendingNy(
                    id = null,
                    opprettet = innsending.opprettet,
                    personident = innsending.personident,
                    soknad = null,
                    data = null,
                    eksternRef = innsending.id,
                    forrigeInnsendingId = null,
                    type = InnsendingType.SOKNAD,
                    journalpost_Id = arkivResponse.journalpostId,
                    filer = innsending.fil.map { fil ->
                        FilNy(
                            tittel = fil.tittel,
                            data = null
                        )
                    }
                )
            )
        }

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


        dataSource.transaction { con ->
            val kobletSoknadUUID =
                con.queryList<UUID>("SELECT * FROM soknad_ettersending WHERE innsending_ettersending_ref = ?") {
                    setParams { setUUID(1, innsending.id) }
                    setRowMapper { row ->
                        row.getUUID("innsending_soknad_ref")
                    }
                }.firstOrNull()
            val kobletSoknadLong = con.queryList<Long>("SELECT * FROM innsending_ny WHERE ekstern_referanse = ?") {
                setParams { setUUID(1, kobletSoknadUUID) }
                setRowMapper { row ->
                    row.getLong("id")
                }
            }.firstOrNull()
            InnsendingRepo(con).lagre(
                InnsendingNy(
                    id = null,
                    opprettet = innsending.opprettet,
                    personident = innsending.personident,
                    soknad = null,
                    data = null,
                    eksternRef = innsending.id,
                    forrigeInnsendingId = kobletSoknadLong,
                    type = InnsendingType.ETTERSENDING,
                    journalpost_Id = arkivResponse.journalpostId,
                    filer = innsending.fil.map { fil ->
                        FilNy(
                            tittel = fil.tittel,
                            data = null
                        )
                    }
                )
            )
        }


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
