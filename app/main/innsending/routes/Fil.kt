package innsending.routes

import innsending.fillager.FillagerClient
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Routing.fil(fillagerClient: FillagerClient) {
    route("/fil") {
        get("/{filreferanse}") {
            call.respond(fillagerClient.hentFil(UUID.fromString(call.parameters["filreferanse"])))
        }

        post {
            call.respond(fillagerClient.opprettFil(call.receive()))
        }

        put("/{filreferanse}") { /* TODO: Endre metadata på en fil (tittel osv) */ }

        delete("/{filreferanse}") {
            fillagerClient.slettFil(UUID.fromString(call.parameters["filreferanse"]))
        }
    }
}