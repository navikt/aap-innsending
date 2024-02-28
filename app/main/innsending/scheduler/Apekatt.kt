package innsending.scheduler

import innsending.Config
import innsending.LeaderElection
import innsending.SECURE_LOGGER
import innsending.arkiv.JournalpostSender
import innsending.kafka.KafkaProducer
import innsending.pdf.PdfGen
import innsending.postgres.InnsendingMedFiler
import innsending.postgres.PostgresRepo
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Apekatt(
    config: Config,
    private val pdfGen: PdfGen,
    private val repo: PostgresRepo,
    private val prometheus: MeterRegistry,
    private val journalpostSender: JournalpostSender,
    private val minsideProducer: KafkaProducer
) {
    private val leaderElector = LeaderElection(config)
    private val mutex = Mutex()
    private var isRunning: Boolean = false

    private fun innsendinger(): Flow<InnsendingMedFiler> = flow {
        while (mutex.withLock { isRunning }) {
            if (leaderElector.isLeader()) {
                if (job.isActive) {
                    prometheus.counter("apekatt.isactive").increment()
                }

                repo.hentAlleInnsendinger()
                    .also { prometheus.gauge("innsendinger", it.size) }
                    .mapNotNull { repo.hentInnsending(it) }
                    .forEach { emit(it) }
            }


            delay(60_000)
        }
    }

    private val job: Job = CoroutineScope(Dispatchers.IO).launch {
        while (this.isActive) {
            innsendinger().collect { innsending ->
                try {
                    if (innsending.data != null) {
                        val pdf = pdfGen.søknadTilPdf(innsending)
                        journalpostSender.arkiverSøknad(pdf, innsending)
                        minsideProducer.produce(innsending.personident)
                    } else {
                        journalpostSender.arkiverEttersending(innsending)
                    }

                    prometheus.counter("innsending", listOf(Tag.of("resultat", "ok"))).increment()
                } catch (cancel: CancellationException) {
                    SECURE_LOGGER.info("Cancellation exception fanget", cancel)
                    throw cancel
                } catch (e: Exception) {
                    SECURE_LOGGER.error("Klarte ikke å arkivere", e)
                    prometheus.counter("innsending", listOf(Tag.of("resultat", "feilet"))).increment()
                    delay(10_000)
                }
            }

            delay(1_000)
        }
    }

    fun start() {
        isRunning = true
    }

    fun stop() {
        isRunning = false

        if (!job.isCompleted) {
            runBlocking {
                job.cancelAndJoin()
            }
        }
    }
}
