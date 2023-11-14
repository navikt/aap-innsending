package innsending.routes

import innsending.postgres.PostgresRepo
import innsending.redis.RedisRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("App")

fun Route.innsendingRoute(postgres: PostgresRepo, redis: RedisRepo){

    post("/søknad/{søknad_id}") {
        val søknadId = UUID.fromString(call.parameters["søknad_id"])
        logger.trace("Mottok søknad med id {}", søknadId)

        postgres.lagreSøknad(
            søknadId = søknadId,
            personident = requireNotNull(call.request.headers["personident"]),
            søknad = call.receive()
        )

        redis.slettMellomlagring(søknadId)

        call.respond(HttpStatusCode.OK, "Vi har mottatt søknaden din")
    }

    post("/vedlegg/{søknad_id}/{vedlegg_id}/{tittel}") {
        val vedleggId = UUID.fromString(call.parameters["vedlegg_id"])
        val vedlegg = redis.hentMellomlagring(vedleggId)
             ?: return@post call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret vedlegg")

        postgres.lagreVedlegg(

            søknadId = UUID.fromString(call.parameters["søknad_id"]),
            vedleggId = vedleggId,
            tittel = requireNotNull(call.parameters["tittel"]),
            vedlegg = vedlegg
        )

        redis.slettMellomlagring(vedleggId)
        call.respond(HttpStatusCode.OK, "Vedlegg ble slettet fra mellomlageret og lagret i databasen")
    }

    delete("/vedlegg/{søknad_id}/{vedlegg_id}") {

    }
}
