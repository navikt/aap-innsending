package innsending.routes

import innsending.redis.Redis
import innsending.scheduler.Apekatt
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusMeterRegistry

fun Routing.actuator(
    prometheus: PrometheusMeterRegistry,
    redisRepo: Redis,
    apekatt: Apekatt,
) {
    route("/actuator") {
        get("/metrics") {
            call.respond(prometheus.scrape())
        }

        get("/live") {
            when {
                apekatt.isLive() -> call.respond(HttpStatusCode.OK, "live")
                else -> call.respond(HttpStatusCode(503, "Service Unavailable"))
            }
        }

        get("/ready") {
            when {
                redisRepo.ready() -> call.respond(HttpStatusCode.OK, "ready")
                else -> call.respond(HttpStatusCode(503, "Service Unavailable"))
            }
        }
    }
}
