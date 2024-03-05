package innsending.scheduler

import innsending.SECURE_LOG
import innsending.arkiv.JournalpostSender
import innsending.kafka.KafkaProducer
import innsending.pdf.PdfGen
import innsending.postgres.InnsendingMedFiler
import innsending.postgres.PostgresRepo
import innsending.redis.LeaderElector
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

class Apekatt(
    private val pdfGen: PdfGen,
    private val repo: PostgresRepo,
    private val prometheus: MeterRegistry,
    private val journalpostSender: JournalpostSender,
    private val minsideProducer: KafkaProducer,
    private val leaderElector: LeaderElector,
) : AutoCloseable {

    private fun innsendinger(): Flow<InnsendingMedFiler> = flow {
        while (true) {
            if (leaderElector.elected()) {
                prometheus.counter("is_apekatt_flowing").increment()

                if (job.isActive) {
                    prometheus.counter("is_apekatt_active").increment()
                }

                repo.hentAlleInnsendinger()
                    .also { prometheus.counter("innsendinger").increment(it.size.toDouble()) }
                    .mapNotNull { repo.hentInnsending(it) }
                    .forEach { emit(it) }
            }

            delay(60_000)
        }
    }

    private val job: Job = CoroutineScope(Dispatchers.IO).launch {
        while (this.isActive) {
            try {
                innsendinger()
                    .distinctUntilChanged()
                    .collect { innsending ->

                        when (innsending.data != null) {
                            true -> arkiverSøknad(innsending)
                            false -> arkiverEttersending(innsending)
                        }

                        prometheus.counter("innsending", listOf(Tag.of("resultat", "ok"))).increment()
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                SECURE_LOG.error("Klarte ikke å arkivere", e)
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

    private suspend fun arkiverEttersending(innsending: InnsendingMedFiler) {
        journalpostSender.arkiverEttersending(innsending)
    }

    private suspend fun arkiverSøknad(innsending: InnsendingMedFiler) {
        val pdf = pdfGen.søknadTilPdf(innsending)
        journalpostSender.arkiverSøknad(pdf, innsending)
        minsideProducer.produce(innsending.personident)
    }
}
