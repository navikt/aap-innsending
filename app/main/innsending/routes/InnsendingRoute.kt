package innsending.routes

import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.route.path.normal.*
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import innsending.db.Repo
import innsending.domene.Fil
import innsending.domene.Innsending
import innsending.domene.NyInnsendingRequest
import java.util.*

data class InnsendingsreferanseRequest(@PathParam("Referanse til innsending") val innsendingsreferanse: UUID)
data class EksternreferanseRequest(@PathParam("Eksternreferanse") val eksternreferanse: UUID)
data class BrukerIdRequest(@QueryParam("Brukers fødselsnummer") val brukerId: String)

fun NormalOpenAPIRoute.innsending(repo: Repo) {
    route("/innsending") {
        route("/{innsendingsreferanse}") {
            get<InnsendingsreferanseRequest, Innsending> { req ->
                respond(repo.hentInnsending(req.innsendingsreferanse))
            }

            put<InnsendingsreferanseRequest, Unit, NyInnsendingRequest> { req, body ->
                repo.oppdaterInnsending(req.innsendingsreferanse, body)
            }

            delete<InnsendingsreferanseRequest, Unit> { req ->
                repo.slettInnsending(req.innsendingsreferanse)
            }

        }

        get<BrukerIdRequest, Innsending> { req ->
            respond(repo.hentInnsendingMedBrukerId(req.brukerId)) //TODO: brukerID fra token?
        }

        route("/eksternreferanse/{eksternreferanse}") {
            get<EksternreferanseRequest, List<Innsending>> { req ->
                respond(repo.hentInnsendingerForEksternreferanse(req.eksternreferanse))
            }
        }

        route("/{innsendingsreferanse}/filer") {
            get<InnsendingsreferanseRequest, List<Fil>> { req ->
                respond(repo.hentAlleFilerForEnInnsending(req.innsendingsreferanse))
            }
        }

        post<Unit, UUID, NyInnsendingRequest> { _, body ->
            val innsendingId = UUID.randomUUID()
            repo.opprettNyInnsending(
                innsendingsreferanse = innsendingId,
                eksternreferanse = body.eksternreferanse,
                brukerId = body.brukerId,
                brevkode = body.innsendingsType
            )
            respond(innsendingId)
        }

        // post("/{innsendingsreferanse}/send_inn") {/* sender inn på kafka */ }
    }
}