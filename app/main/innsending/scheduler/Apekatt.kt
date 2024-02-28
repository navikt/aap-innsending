package innsending.scheduler

import innsending.Config
import innsending.LeaderElection
import innsending.SECURE_LOG
import innsending.arkiv.JournalpostSender
import innsending.http.HttpResult
import innsending.kafka.KafkaProducer
import innsending.pdf.Pdf
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

            delay(1_000)
        }
    }

    private val job: Job = CoroutineScope(Dispatchers.IO).launch {
        while (this.isActive) {
            innsendinger().collect { innsending ->
                try {
                    when (innsending.data != null) {
                        true -> arkiverSøknad(innsending)
                        false -> arkiverEttersending(innsending)
                    }

                    prometheus.counter("innsending", listOf(Tag.of("resultat", "ok"))).increment()
                } catch (cancel: CancellationException) {
                    SECURE_LOG.info("Cancellation exception fanget", cancel)
                    throw cancel
                } catch (e: Exception) {
                    SECURE_LOG.error("Klarte ikke å arkivere", e)
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

    private fun arkiverEttersending(innsending: InnsendingMedFiler) {
        journalpostSender.arkiverEttersending(innsending)
    }

    private suspend fun arkiverSøknad(innsending: InnsendingMedFiler) {
        val pdf = when (val res = pdfGen.søknadTilPdf(innsending)) {
            is HttpResult.Ok -> res.getOrNull<Pdf>()
            is HttpResult.ClientError -> res.traceError()
            is HttpResult.ServerError -> res.traceError()
            null -> null.also {
                SECURE_LOG.error("Klarte ikke sende søknad til PdfGen.")
            }
        } ?: error("Feilet arkivering av søknad, prøv igjen...")

        journalpostSender.arkiverSøknad(pdf, innsending)
        minsideProducer.produce(innsending.personident)
    }
}
