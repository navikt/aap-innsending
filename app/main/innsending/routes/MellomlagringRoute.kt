package innsending.routes

import innsending.redis.RedisRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.mellomlagerRoute(redis: RedisRepo) {

    post("/vedlegg/{vedleggId}") {
        val vedleggId = requireNotNull(UUID.fromString(call.parameters["vedleggId"]))

        // todo: virusscan
        // todo: pdfgen
        redis.mellomlagre(
            key = vedleggId,
            value = call.receive()
        )

        call.respond(HttpStatusCode.Created, "Vedlegg ble mellomlagret")
    }

    get("/vedlegg/{vedleggId}") {
        val vedleggId = requireNotNull(UUID.fromString(call.parameters["vedleggId"]))

        when (val vedlegg = redis.hentMellomlagring(vedleggId)) {
            null -> call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret vedlegg")
            else -> call.respond(HttpStatusCode.OK, vedlegg)
        }
    }
}
