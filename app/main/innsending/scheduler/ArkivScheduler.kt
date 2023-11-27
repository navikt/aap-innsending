package innsending.scheduler

import innsending.arkiv.JournalpostSender
import innsending.postgres.PostgresRepo
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Scheduler")
private const val TI_SEKUNDER = 10_000L

class ArkivScheduler(
    private val repo: PostgresRepo,
    private val journalpostSender: JournalpostSender
): AutoCloseable {
    private val job: Job = CoroutineScope(Dispatchers.Default).launch {
        while (this.isActive) {
            try {
                val liste = repo.hentAlleInnsendinger()
                liste.forEach {  søknadId ->
                    logger.info("Prøver å arkivere....")
                    // TODO Lage PDF av søknaden
                    journalpostSender.arkiverAltSomKanArkiveres(søknadId)
                }
            } catch (t: Throwable) {
                logger.error("Klarte ikke å arkivere", t)
            }
            delay(TI_SEKUNDER)
        }
    }

    override fun close() {
        if(!job.isCompleted) {
            runBlocking {
                job.cancelAndJoin()
            }
        }
    }
}
