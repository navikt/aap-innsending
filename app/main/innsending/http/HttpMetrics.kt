package innsending.http

import innsending.SECURE_LOGGER
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer

private val registredMeters: MutableMap<String, HttpMetric> = mutableMapOf()

fun MeterRegistry.createTimer(
    name: String,
): HttpMetric.TIMER {
    return if (registredMeters.containsKey(name)) {
        SECURE_LOGGER.warn("Timer with name $name already exists")
        registredMeters[name] as HttpMetric.TIMER
    } else {
        HttpMetric.TIMER(name, this).also {
            registredMeters[name] = it
        }
    }
}

fun MeterRegistry.createCounter(
    name: String,
    tags: List<String>,
): HttpMetric.COUNTER {
    return if (registredMeters.containsKey(name)) {
        SECURE_LOGGER.warn("Counter with name $name already exists")
        registredMeters[name] as HttpMetric.COUNTER
    } else {
        HttpMetric.COUNTER(name, tags, this).also {
            registredMeters[name] = it
        }
    }
}

sealed interface HttpMetric {
    class COUNTER(
        private val name: String,
        private val keys: List<String>,
        private val registry: MeterRegistry,
    ) : HttpMetric {
        fun inc(tags: List<String>) {
            val tagList = keys.zip(tags).map { (key, value) -> Tag.of(key, value) }
            registry.counter(name, tagList).increment()
        }
    }

    class TIMER(
        private val name: String,
        private val registry: MeterRegistry,
    ) : HttpMetric {

        suspend fun <T> timer(process: suspend () -> T): T =
            Timer.resource(registry, name)
                .use {
                    process()
                }
    }
}
