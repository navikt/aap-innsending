package innsending

import io.micrometer.core.instrument.DistributionSummary
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
}