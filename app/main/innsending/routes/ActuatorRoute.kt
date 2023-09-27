package innsending.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.kafka.streams.v2.Streams

fun Routing.actuator(prometheus: PrometheusMeterRegistry, kafka: Streams) {
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