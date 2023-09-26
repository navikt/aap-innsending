package innsending.routes

import innsending.db.Repo
import innsending.domene.NyInnsendingRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Routing.innsending(repo: Repo) {
    route("/innsending") {
        get("/{innsendingsreferanse}") {
            call.respond(repo.hentInnsending(UUID.fromString(call.parameters["innsendingsreferanse"])))
        }

        get {
            val innsending =
                repo.hentInnsendingMedBrukerId(call.request.queryParameters["brukerId"]!!)//TODO: brukerID fra token?
            call.respond(innsending)
        }

        get("/eksternreferanse/{eksternreferanse}") {
            call.respond(repo.hentInnsendingerForEksternreferanse(UUID.fromString(call.parameters["innsendingsreferanse"])))
        }

        get("/{innsendingsreferanse}/filer") {
            call.respond(repo.hentAlleFilerForEnInnsending(UUID.fromString(call.parameters["innsendingsreferanse"])))
        }

        post {
            val innsending = call.receive<NyInnsendingRequest>()
            val innsendingId = UUID.randomUUID()
            repo.opprettNyInnsending(
                innsendingsreferanse = innsendingId,
                eksternreferanse = innsending.eksternreferanse,
                brukerId = innsending.brukerId,
                brevkode = innsending.innsendingsType
            )
            call.respond(HttpStatusCode.Created, innsendingId)
        }

        post("/{innsendingsreferanse}/send_inn") {/* sender inn på kafka */ }

        put("/{innsendingsreferanse}") {
            val innsending = call.receive<NyInnsendingRequest>()
            val innsedingsreferanse = repo.hentInnsendingMedBrukerId(innsending.brukerId).innsendingsreferanse

            repo.oppdaterInnsending(innsedingsreferanse, innsending) //TODO: Vi trenger token her også

            call.respond(HttpStatusCode.OK)
        }

        delete("/{innsendingsreferanse}") {
            repo.slettInnsending(UUID.fromString(call.parameters["innsendingsreferanse"]))
            call.respond(HttpStatusCode.OK)
        }
    }
}