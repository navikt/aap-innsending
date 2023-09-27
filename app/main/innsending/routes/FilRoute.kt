package innsending.routes

import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.delete
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import innsending.fillager.FillagerClient
import java.util.*

data class FilrefaranseRequest(@PathParam("") val filreferanse: UUID)

fun NormalOpenAPIRoute.fil(fillagerClient: FillagerClient) {
    route("/fil") {
        route("/{filreferanse}") {
            get<FilrefaranseRequest, ByteArray> { req ->
                respond(fillagerClient.hentFil(req.filreferanse))
            }
        }

        post<Unit, UUID, ByteArray> { _, body ->
            respond(fillagerClient.opprettFil(body))
        }

        //put("/{filreferanse}") { TODO: Endre metadata på en fil (tittel osv)  }

        route("/{filreferanse}") {
            delete<FilrefaranseRequest, Unit> { req ->
                fillagerClient.slettFil(req.filreferanse)
            }
        }
    }
}