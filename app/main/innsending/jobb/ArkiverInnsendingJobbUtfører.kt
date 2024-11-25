package innsending.jobb

import innsending.db.InnsendingRepo
import innsending.jobb.arkivering.ArkiveringService
import innsending.jobb.arkivering.JoarkClient
import innsending.pdf.PdfGenClient
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører

class ArkiverInnsendingJobbUtfører(
    val innsendingRepo: InnsendingRepo,
    val arkiveringService: ArkiveringService
) : JobbUtfører {

    override fun utfør(input: JobbInput) {
        val innsendingId = input.sakId()
        val arkivResponse = arkiveringService.arkiverInnsending(innsendingId)
        innsendingRepo.markerFerdig(innsendingId, arkivResponse.journalpostId)
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            val innsendingRepo = InnsendingRepo(connection)

            return ArkiverInnsendingJobbUtfører(
                innsendingRepo,
                ArkiveringService(
                    innsendingRepo,
                    pdfGen = PdfGenClient(),
                    joarkClient = JoarkClient()
                    )
            )
        }

        override fun type(): String {
            return "innsending.arkiver"
        }

        override fun navn(): String {
            return "Prosesser behandling"
        }

        override fun beskrivelse(): String {
            return "Ansvarlig for å drive prosessen på en gitt behandling"
        }
    }
}