package innsending.routes

import innsending.redis.JedisRedis
import innsending.redis.Redis
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusMeterRegistry

fun Routing.actuator(prometheus: PrometheusMeterRegistry, redisRepo: Redis) {
    route("/actuator") {
        get("/metrics") {
            call.respond(prometheus.scrape())
        }
        get("/live") {
            call.respond(HttpStatusCode.OK, "live")
        }
        get("/ready") {
            if(redisRepo.ready()){
                call.respond(HttpStatusCode.OK, "ready")
            }
            call.respond(HttpStatusCode(503, "Service Unavailable"))
        }
    }
}