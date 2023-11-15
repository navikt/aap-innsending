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

fun Route.innsendingRoute(postgres: PostgresRepo, redis: RedisRepo) {
    route("/innsending") {

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

        post("/vedlegg") {
            val innsending = call.receive<InnsendingVedlegg>()
            val vedlegg = redis.hentMellomlagring(innsending.vedleggId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret vedlegg")

            postgres.lagreVedlegg(

                søknadId = innsending.soknadId,
                vedleggId = innsending.vedleggId,
                tittel = innsending.tittel,
                vedlegg = vedlegg
            )

            redis.slettMellomlagring(innsending.vedleggId)

            call.respond(HttpStatusCode.OK, "Vedlegg ble slettet fra mellomlageret og lagret i databasen")
        }
    }
}

data class InnsendingVedlegg(
    val soknadId: UUID,
    val vedleggId: UUID,
    val tittel: String,
)