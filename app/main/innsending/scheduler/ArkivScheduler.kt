package innsending.scheduler

import innsending.arkiv.JournalpostSender
import innsending.postgres.PostgresRepo
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

private val logger = LoggerFactory.getLogger("Scheduler")
private const val TI_SEKUNDER = 10_000L

class ArkivScheduler(
    private val postgresRepo: PostgresRepo,
    private val journalpostSender: JournalpostSender
): AutoCloseable {
    private val job: Job = CoroutineScope(Dispatchers.Default).launch {
        while (this.isActive) {
            logger.info("Prøver å arkivere....")
            try {
                val liste = postgresRepo.hentAlleInnsendinger()
                liste.forEach {  søknadId ->
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
