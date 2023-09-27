package innsending.routes

import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.delete
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import innsending.fillager.FillagerClient
import java.util.*

data class FilrefaranseParams(@PathParam("") val filreferanse: UUID)

fun NormalOpenAPIRoute.fil(fillagerClient: FillagerClient) {
    route("/fil") {
        route("/{filreferanse}") {
            get<FilrefaranseParams, ByteArray>(
                info(summary = "Hent fil", description = "Hent ut en fil basert på filreferanse")
            ) { params ->
                respond(fillagerClient.hentFil(params.filreferanse))
            }
        }

        post<Unit, UUID, ByteArray>(
            info(summary = "Lagre fil", description = "Lagre en fil")
        ) { _, body ->
            respond(fillagerClient.opprettFil(body))
        }

        //put("/{filreferanse}") { TODO: Endre metadata på en fil (tittel osv)  }

        route("/{filreferanse}") {
            delete<FilrefaranseParams, Unit>(
                info(summary = "Slett fil", description = "Sletter en fil basert på filreferanse")
            ) { params ->
                fillagerClient.slettFil(params.filreferanse)
            }
        }
    }
}