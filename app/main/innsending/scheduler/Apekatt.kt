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
) : AutoCloseable {
    private val leaderElector = LeaderElection(config)
    private val mutex = Mutex()
    private var isRunning: Boolean = false

    private fun innsendinger(): Flow<InnsendingMedFiler> = flow {
        while (true) {
            if (leaderElector.isLeader()) {
                prometheus.counter("is_apekatt_flowing").increment()
                repo.hentAlleInnsendinger()
                    .also { prometheus.gauge("innsendinger", it.size) }
                    .mapNotNull { repo.hentInnsending(it) }
                    .forEach {
                        emit(it)
                    }
            }

            delay(60_000)
        }
    }

    private val job: Job = CoroutineScope(Dispatchers.IO).launch {
        while (this.isActive) {
            try {
                innsendinger().collect { innsending ->
                    if (innsending.data != null) {
                        val pdf = pdfGen.søknadTilPdf(innsending)
                        journalpostSender.arkiverSøknad(pdf, innsending)
                        minsideProducer.produce(innsending.personident)
                    } else {
                        journalpostSender.arkiverEttersending(innsending)
                    }

                    prometheus.counter("innsending", listOf(Tag.of("resultat", "ok"))).increment()
                    mutex.withLock {
                        isRunning = true
                        prometheus.counter("is_apekatt_active").increment()
                    }
                }
            } catch (e: Exception) {
                mutex.withLock { isRunning = false }
                if (e is CancellationException) throw e
                SECURE_LOGGER.error("Klarte ikke å arkivere", e)
                prometheus.counter("innsending", listOf(Tag.of("resultat", "feilet"))).increment()
                delay(10_000)
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
