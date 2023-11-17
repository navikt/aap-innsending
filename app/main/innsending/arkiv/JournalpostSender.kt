package innsending.arkiv

import innsending.postgres.InnsendingMedFiler
import innsending.postgres.PostgresRepo
import java.util.*

class JournalpostSender(
    private val client: JoarkClient,
    private val postgresRepo: PostgresRepo
) {

    fun arkiverAltSomKanArkiveres(søknadId: UUID) {
        val innsending: InnsendingMedFiler = postgresRepo.hentInnsending(søknadId)

        val journalpost = Journalpost(
            tittel = "Søknad AAP",
            avsenderMottaker = Journalpost.AvsenderMottaker(
                id = Journalpost.Fødselsnummer(innsending.personident)
            ),
            bruker = Journalpost.Bruker(
                id = Journalpost.Fødselsnummer(innsending.personident)
            ),
            dokumenter = lagDokumentliste(innsending),
            eksternReferanseId = innsending.id.toString()
        )

        val journalført = client.opprettJournalpost(journalpost, innsending.id.toString())
        if (journalført) {
            postgresRepo.slettInnsending(innsending.id)
        }
    }

    private fun lagDokumentliste(innsending: InnsendingMedFiler): List<Journalpost.Dokument> {
        val søknad =
            Journalpost.Dokument(
                tittel = "Søknad",
                brevkode = "NAV 11-13.05",
                dokumentVarianter = listOf(
                    Journalpost.DokumentVariant(
                        fysiskDokument = Base64.getEncoder().encodeToString(innsending.data)
                    )
                )
            )

        val vedlegg = innsending.vedlegg.map { vedlegg ->
            Journalpost.Dokument(
                tittel = vedlegg.tittel,
                brevkode = "NAV 11-13.05",
                dokumentVarianter = listOf(
                    Journalpost.DokumentVariant(
                        fysiskDokument = Base64.getEncoder().encodeToString(vedlegg.data)
                    )
                )
            )
        }
        return listOf(søknad) + vedlegg
    }
}
