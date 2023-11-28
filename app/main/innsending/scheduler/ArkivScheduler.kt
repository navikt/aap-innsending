package innsending.scheduler

import innsending.arkiv.JournalpostSender
import innsending.pdf.PdfGen
import innsending.postgres.InnsendingMedFiler
import innsending.postgres.PostgresRepo
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Scheduler")
private const val TI_SEKUNDER = 10_000L

class ArkivScheduler(
    private val pdfGen: PdfGen,
    private val repo: PostgresRepo,
    private val journalpostSender: JournalpostSender
) : AutoCloseable {
    private val job: Job = CoroutineScope(Dispatchers.Default).launch {
        while (this.isActive) {
            try {
                val liste = repo.hentAlleInnsendinger()
                liste.forEach { søknadId ->
                    logger.info("Prøver å arkivere....")

                    val innsending = repo.hentInnsending(søknadId)

                    val søknadSomPdf = runBlocking {
                        pdfGen.søknadTilPdf(innsending.data)
                    }

                    journalpostSender.arkiverAltSomKanArkiveres(søknadSomPdf, innsending)
                }
            } catch (t: Throwable) {
                logger.error("Klarte ikke å arkivere", t)
            }
            delay(TI_SEKUNDER)
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
