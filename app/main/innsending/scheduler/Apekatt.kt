package innsending.scheduler


import innsending.Config
import innsending.LeaderElection
import innsending.SECURE_LOGGER
import innsending.arkiv.JournalpostSender
import innsending.http.HttpClientFactory
import innsending.kafka.KafkaProducer
import innsending.kafka.KafkaProducerException
import innsending.pdf.PdfGen
import innsending.postgres.PostgresRepo
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow

private const val TI_SEKUNDER = 10_000L

class Apekatt(
    private val config: Config,
    private val pdfGen: PdfGen,
    private val repo: PostgresRepo,
    private val prometheus: MeterRegistry,
    private val journalpostSender: JournalpostSender,
    private val minsideProducer: KafkaProducer
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val leaderElector = LeaderElection(config, HttpClientFactory.create())

    // Only initialize for pod marked as elected leader
    private lateinit var job: Job
    private var isRunning = false

    fun start() {
        isRunning = true
        val flow = flow {
            while (isRunning) {
                if (leaderElector.isLeader()) {
                    val innsendingIder = repo.hentAlleInnsendinger()
                    prometheus.gauge("innsendinger", innsendingIder.size)
                    innsendingIder.forEach { id -> emit(id) }
                }
                delay(1000)
            }
        }

        job = scope.launch {
            while (this.isActive && isRunning) {
                try {
                    flow.collect { innsendingId ->
                        prometheus.counter("apekatt.isactive").increment()

                        val innsending = repo.hentInnsending(innsendingId)

                        if (innsending == null) {
                            SECURE_LOGGER.warn(
                                """
                            Failed to archive innsending=$innsendingId
                            Not found in database. Already archived?
                            """.trimIndent()
                            )
                            return@collect
                        }

                        if (innsending.data != null) {
                            val pdf = pdfGen.søknadTilPdf(innsending)
                            journalpostSender.arkiverSøknad(pdf, innsending)

                            minsideProducer.produce(innsending.personident)
                        } else {
                            journalpostSender.arkiverEttersending(innsending)
                        }

                        prometheus.counter("innsending", listOf(Tag.of("resultat", "ok"))).increment()
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) {
                        SECURE_LOGGER.info("Cancellation exception fanget", t)
                        throw t
                    }
                    if (t is KafkaProducerException) {
                        continue
                    }

                    SECURE_LOGGER.error("Klarte ikke å arkivere", t)
                    prometheus.counter("innsending", listOf(Tag.of("resultat", "feilet"))).increment()

                    delay(TI_SEKUNDER)
                }
                delay(TI_SEKUNDER)
            }
        }
    }

    fun stop() {
        isRunning = false

        if (::job.isInitialized && !job.isCompleted) {
            runBlocking {
                job.cancelAndJoin()
            }
        }
    }
}
