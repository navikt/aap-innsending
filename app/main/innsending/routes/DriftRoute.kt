package innsending.routes

import innsending.redis.HashedKey
import innsending.redis.Redis
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Routing.driftApi(redis: Redis) {
    route("/api/drift") {
        get("/redis/memory") {
            call.respond(HttpStatusCode.OK, redis.getAllKeysWithMemory())
        }

        delete("/redis/{hashedKey}") {
            val key = HashedKey(requireNotNull(call.parameters["hashedKey"])).toKey()
            if (redis.exists(key)) {
                redis.del(key)
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}