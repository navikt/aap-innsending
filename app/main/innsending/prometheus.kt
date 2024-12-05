package innsending

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object prometheus {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}