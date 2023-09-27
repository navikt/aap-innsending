package innsending.routes

import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.*
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import innsending.db.Repo
import innsending.domene.Fil
import innsending.domene.Innsending
import innsending.domene.NyInnsendingRequest
import java.util.*

data class InnsendingsreferanseParams(@PathParam("Referanse til innsending") val innsendingsreferanse: UUID)
data class EksternreferanseParams(@PathParam("Eksternreferanse") val eksternreferanse: UUID)
data class BrukerIdParams(@QueryParam("Brukers fødselsnummer") val brukerId: String)

fun NormalOpenAPIRoute.innsending(repo: Repo) {
    route("/innsending") {
        route("/{innsendingsreferanse}") {
            get<InnsendingsreferanseParams, Innsending>(
                info(summary = "Hent innsending", description = "Hent ut en innsending basert på referanse")
            ) { params ->
                respond(repo.hentInnsending(params.innsendingsreferanse))
            }

            put<InnsendingsreferanseParams, Unit, NyInnsendingRequest>(
                info(summary = "Oppdater innsending", description = "Oppdater en innsending basert på referanse")
            ) { params, body ->
                repo.oppdaterInnsending(params.innsendingsreferanse, body)
            }

            delete<InnsendingsreferanseParams, Unit>(
                info(summary = "Slett innsending", description = "Sletter en innsending basert på referanse")
            ) { params ->
                repo.slettInnsending(params.innsendingsreferanse)
            }

        }

        get<BrukerIdParams, Innsending>(
            info(summary = "Hent innsending", description = "Hent en innsending basert på brukers fødselsnummer")
        ) { params ->
            respond(repo.hentInnsendingMedBrukerId(params.brukerId)) //TODO: brukerID fra token?
        }

        route("/eksternreferanse/{eksternreferanse}") {
            get<EksternreferanseParams, List<Innsending>>(
                info(summary = "Hent innsendinger", description = "Hent innsendinger basert på eksternreferanse")
            ) { params ->
                respond(repo.hentInnsendingerForEksternreferanse(params.eksternreferanse))
            }
        }

        route("/{innsendingsreferanse}/filer") {
            get<InnsendingsreferanseParams, List<Fil>>(
                info(summary = "Hent filer", description = "Hent alle filer for en innsendingsreferanse")
            ) { params ->
                respond(repo.hentAlleFilerForEnInnsending(params.innsendingsreferanse))
            }
        }

        post<Unit, UUID, NyInnsendingRequest>(
            info(summary = "Opprett innsending", description = "Opprett en ny innsending")
        ) { _, body ->
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