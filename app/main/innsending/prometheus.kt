package innsending

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object prometheus {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private val størrelseDistribution: DistributionSummary =
        DistributionSummary.builder("innsending_vedlegg_storrelse")
            .publishPercentileHistogram().register(prometheus)

    fun registrerVedleggStørrelse(størrelse: Long) {
        størrelseDistribution.record(størrelse.toDouble())
    }

    fun MeterRegistry.arkivertTeller(type: String): Counter =
        this.counter("innsending_arkivert_total", listOf(Tag.of("type", type)))
}