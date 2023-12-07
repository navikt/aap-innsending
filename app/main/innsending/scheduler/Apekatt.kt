package innsending.scheduler


import innsending.SECURE_LOGGER
import innsending.arkiv.JournalpostSender
import innsending.pdf.PdfGen
import innsending.postgres.PostgresRepo
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Scheduler")
private const val TI_SEKUNDER = 10_000L

class Apekatt(
    private val pdfGen: PdfGen,
    private val repo: PostgresRepo,
    private val prometheus: MeterRegistry,
    private val journalpostSender: JournalpostSender
) : AutoCloseable {
    private val job: Job = CoroutineScope(Dispatchers.Default).launch {
        while (this.isActive) {
            try {
                val innsendingIder = repo.hentAlleInnsendinger()
                logger.info("Fant {} usendte innsendinger", innsendingIder.size)
                prometheus.gauge("innsendinger", innsendingIder.size)
                innsendingIder.forEach { innsendingId ->
                    val innsending = repo.hentInnsending(innsendingId)

                    if (innsending.data != null) {
                        logger.info("Prøver å arkivere søknad {}", innsending.id)
                        val pdf = pdfGen.søknadTilPdf(innsending.data)
                        journalpostSender.arkiverSøknad(pdf, innsending)
                    } else {
                        logger.info("Prøver å arkivere ettersending {}", innsending.id)
                        journalpostSender.arkiverEttersending(innsending)
                    }
                    prometheus.counter("innsending", listOf(Tag.of("resultat", "ok"))).increment()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t

                SECURE_LOGGER.error("Klarte ikke å arkivere", t)
                prometheus.counter("innsending", listOf(Tag.of("resultat", "feilet"))).increment()

                delay(TI_SEKUNDER)
            }
        }
    }

    override fun close() {
        if (!job.isCompleted) {
            runBlocking {
                job.cancelAndJoin()
            }
        }
    }
}
