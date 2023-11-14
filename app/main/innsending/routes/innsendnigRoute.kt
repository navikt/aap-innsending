package innsending.routes

import innsending.postgres.PostgresRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("App")

fun Route.innsendingRoute(postgres: PostgresRepo) {

    post("/send_inn/{søknad_id}") {
        val søknadId = UUID.fromString(call.parameters["søknad_id"])
        logger.trace("Mottok søknad med id {}", søknadId)

        postgres.lagreSøknad(
            søknadId = søknadId,
            personident = requireNotNull(call.request.headers["personident"]),
            søknad = call.receive()
        )

        call.respond(HttpStatusCode.OK, "Vi har mottatt søknaden din")
    }

    post("/vedlegg/{søknad_id}/{vedlegg_id}") {

    }

    delete("/vedlegg/{søknad_id}/{vedlegg_id}") {

    }
}
