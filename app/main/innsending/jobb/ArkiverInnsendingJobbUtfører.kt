package innsending.jobb

import innsending.db.InnsendingNy
import innsending.db.InnsendingRepo
import innsending.jobb.arkivering.ArkiveringService
import innsending.jobb.arkivering.JoarkClient
import innsending.pdf.PdfGenClient
import innsending.postgres.InnsendingType
import innsending.prometheus
import io.micrometer.core.instrument.Tag
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører

class ArkiverInnsendingJobbUtfører(
    val innsendingRepo: InnsendingRepo,
    val arkiveringService: ArkiveringService,
    val flytJobbRepository: FlytJobbRepository,
) : JobbUtfører {
    override fun utfør(input: JobbInput) {
        val innsendingId = input.sakId()
        val innsending = innsendingRepo.hent(innsendingId)

        if (innsendingId == 256521L) {
            val innsendinger = splittOppInnsending(innsending)
            var innsendingNr = 1
            var journalpostId = ""
            for (innsendingNy in innsendinger) {
                val jpId = arkiveringService.arkiverEttersendelseInnsendingSpesielhåndtering(
                    innsendingNy,
                    innsendingNr
                )
                if (jpId != null) {
                    journalpostId = jpId
                }
                innsendingNr = innsendingNr++
            }
            innsendingRepo.markerFerdig(innsendingId, journalpostId)
        } else {
            val arkivResponse = when (innsending.type) {
                InnsendingType.SOKNAD -> {
                    arkiveringService.arkiverSøknadInnsending(innsending)
                }

                InnsendingType.ETTERSENDING -> {
                    arkiveringService.arkiverEttersendelseInnsending(innsending)
                }
            }
            innsendingRepo.markerFerdig(innsendingId, arkivResponse.journalpostId)
        }

        if (innsending.type == InnsendingType.SOKNAD) {
            // Planlegg ny jobb
            flytJobbRepository.leggTil(JobbInput(MinSideNotifyJobbUtfører).forSak(innsendingId))
        }
        prometheus.prometheus.counter("innsending", listOf(Tag.of("resultat", "ok"))).increment()
    }

    private fun splittOppInnsending(innsendingNy: InnsendingNy): List<InnsendingNy> {
        return innsendingNy.filer.sortedBy { it.id }.chunked(10).map { listerMedFiler ->
            InnsendingNy(
                innsendingNy.id,
                innsendingNy.opprettet,
                innsendingNy.personident,
                innsendingNy.soknad,
                innsendingNy.data,
                innsendingNy.eksternRef,
                innsendingNy.forrigeInnsendingId,
                innsendingNy.type,
                null,
                listerMedFiler
            )
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
