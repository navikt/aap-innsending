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

        post("/søknad") {
            val personIdent = "<personIdent>"
            val innsending = call.receive<Innsending>()
            val søknadId = UUID.randomUUID()
            logger.trace("Mottok søknad med id")

            val vedleggMedDataPairs = innsending.vedlegg.mapNotNull { vedlegg ->
                redis.hentMellomlagring(vedlegg.id)?.let { Pair(vedlegg, it) }
            }

            if(vedleggMedDataPairs.size != innsending.vedlegg.size ){
                call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret vedlegg")
            }

            postgres.lagreSøknadMedVedlegg(
                søknadId = søknadId,
                personIdent = personIdent,
                innsending = innsending,
                vedlegg = vedleggMedDataPairs
            )

            innsending.vedlegg.forEach { vedlegg ->
                redis.slettMellomlagring(vedlegg.id)
            }
            redis.slettMellomlagring(personIdent)

            call.respond(HttpStatusCode.OK, "Vi har mottatt søknaden din")
        }

        post("/vedlegg") {
            val innsending = call.receive<InnsendingVedlegg>()
            val vedlegg = redis.hentMellomlagring(innsending.vedleggId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret vedlegg")

            postgres.lagreVedlegg(
                søknadId = UUID.fromString(innsending.soknadId),
                vedleggId = UUID.fromString(innsending.vedleggId),
                tittel = innsending.tittel,
                vedlegg = vedlegg
            )

            redis.slettMellomlagring(innsending.vedleggId)

            call.respond(HttpStatusCode.OK, "Vedlegg ble slettet fra mellomlageret og lagret i databasen")
        }
    }
}

data class InnsendingVedlegg(
    val soknadId: String,
    val vedleggId: String,
    val tittel: String,
)

data class Innsending(
    val soknad: ByteArray,
    val vedlegg: List<Vedlegg>,
)

data class Vedlegg(
    val id: String,
    val tittel: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vedlegg

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
