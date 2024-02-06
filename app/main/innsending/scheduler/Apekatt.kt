package innsending.scheduler


import innsending.SECURE_LOGGER
import innsending.arkiv.JournalpostSender
import innsending.pdf.PdfGen
import innsending.postgres.PostgresRepo
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.*

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
                prometheus.gauge("innsendinger", innsendingIder.size)
                innsendingIder.forEach { innsendingId ->
                    val innsending = repo.hentInnsending(innsendingId)

                    if (innsending.data != null) {
                        val pdf = pdfGen.søknadTilPdf(innsending.data, innsending.opprettet)
                        journalpostSender.arkiverSøknad(pdf, innsending)
                    } else {
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
