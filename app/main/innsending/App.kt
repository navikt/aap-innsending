package innsending

import innsending.kafka.Topics
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.kafka.streams.v2.KafkaStreams
import no.nav.aap.kafka.streams.v2.Streams
import no.nav.aap.kafka.streams.v2.Topology
import no.nav.aap.kafka.streams.v2.config.StreamsConfig
import no.nav.aap.ktor.config.loadConfig
import org.slf4j.LoggerFactory

private val secureLog = LoggerFactory.getLogger("secureLog")
data class Config(
    val kafka: StreamsConfig
)
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

fun Application.server(kafka: Streams = KafkaStreams()) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val config = loadConfig<Config>()

    install(MicrometerMetrics) { registry = prometheus }

    Thread.currentThread().setUncaughtExceptionHandler { _, e -> secureLog.error("Uhåndtert feil", e) }
    environment.monitor.subscribe(ApplicationStopping) { kafka.close() }

    kafka.connect(
        config = config.kafka,
        registry = prometheus,
        topology = topology()
    )

    routing {
        route("/actuator") {
            get("/metrics") {
                call.respond(prometheus.scrape())
            }
            get("/live") {
                val status = if (kafka.live()) HttpStatusCode.OK else HttpStatusCode.InternalServerError
                call.respond(status, "vedtak")
            }
            get("/ready") {
                val status = if (kafka.ready()) HttpStatusCode.OK else HttpStatusCode.InternalServerError
                call.respond(status, "vedtak")
            }

        }
    }
}

internal fun topology(): Topology {
    return no.nav.aap.kafka.streams.v2.topology {
        consume(Topics.innsending)

    }
}