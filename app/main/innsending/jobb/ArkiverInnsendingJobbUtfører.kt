package innsending.jobb

import innsending.db.InnsendingRepo
import innsending.jobb.arkivering.ArkiveringService
import innsending.jobb.arkivering.JoarkClient
import innsending.pdf.PdfGenClient
import innsending.postgres.InnsendingType
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører

class ArkiverInnsendingJobbUtfører(
    val innsendingRepo: InnsendingRepo,
    val arkiveringService: ArkiveringService,
    val flytJobbRepository: FlytJobbRepository
) : JobbUtfører {

    override fun utfør(input: JobbInput) {
        val innsendingId = input.sakId()
        val innsending = innsendingRepo.hent(innsendingId)

        val arkivResponse = when (innsending.type) {
            InnsendingType.SOKNAD -> {
                arkiveringService.arkiverSøknadInnsending(innsending)
            }

            InnsendingType.ETTERSENDING -> {
                arkiveringService.arkiverEttersendelseInnsending(innsending)
            }
        }

        innsendingRepo.markerFerdig(innsendingId, arkivResponse.journalpostId)

        if (innsending.type == InnsendingType.SOKNAD) {
            // Planlegg ny jobb
            flytJobbRepository.leggTil(JobbInput(MinSideNotifyJobbUtfører).forSak(innsendingId))
        }
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            val innsendingRepo = InnsendingRepo(connection)

            return ArkiverInnsendingJobbUtfører(
                innsendingRepo,
                ArkiveringService(
                    pdfGen = PdfGenClient(),
                    joarkClient = JoarkClient()
                ),
                FlytJobbRepository(connection)
            )
        }

        override fun type(): String {
            return "innsending.arkiver"
        }

        override fun navn(): String {
            return "Arkiver innsending"
        }

        override fun beskrivelse(): String {
            return "Konverterer til PDF hvis behov og arkiverer innsendingen"
        }
    }
}
