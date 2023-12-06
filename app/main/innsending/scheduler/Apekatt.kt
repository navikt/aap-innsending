package innsending.scheduler


import innsending.SECURE_LOGGER
import innsending.arkiv.JournalpostSender
import innsending.pdf.PdfGen
import innsending.postgres.PostgresRepo
import io.micrometer.core.instrument.MeterRegistry
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
            logger.info("I'm alive!")
            try {
                val innsendingIder = repo.hentAlleInnsendinger()
                logger.info("Fant {} usendte innsendinger", innsendingIder.size)
                prometheus.gauge("innsendinger", innsendingIder.size)
                innsendingIder.forEach { innsendingId ->
                    logger.info("Prøver å arkivere....")

                    val innsending = repo.hentInnsending(innsendingId)

                    logger.info("Prøver å arkivere {}", innsending.id)
                    if (innsending.data != null) {
                        runBlocking {
                            val pdf = pdfGen.søknadTilPdf(innsending.data)
                            journalpostSender.arkiverSøknad(pdf, innsending)
                        }
                    } else {
                        journalpostSender.arkiverEttersending(innsending)
                    }
                    logger.info("Ferdig")
                }
            } catch (t: Throwable) {
                SECURE_LOGGER.error("Klarte ikke å arkivere", t)
                prometheus.counter("innsendinger_feilet").increment()
            }
            delay(TI_SEKUNDER)
        }
        logger.info("I'm dead :(")
    }

    override fun close() {
        if (!job.isCompleted) {
            runBlocking {
                job.cancelAndJoin()
            }
        }
    }
}
