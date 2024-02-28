package innsending.http

import io.prometheus.client.Summary

sealed interface Meter {

    class LATENCY(
        private val name: String,
        private val description: String,
    ) : Meter {
        private val latency by lazy {
            Summary.build()
                .name(name)
                .quantile(0.5, 0.05) // Add 50th percentile (= median) with 5% tolerated error
                .quantile(0.9, 0.01) // Add 90th percentile with 1% tolerated error
                .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
                .help(description)
                .register()
        }

        suspend fun <T> timed(block: suspend () -> T): T =
            latency.startTimer().use {
                block()
            }
    }
}
