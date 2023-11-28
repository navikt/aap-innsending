package innsending.routes

import innsending.postgres.PostgresRepo
import innsending.redis.Redis
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

private val logger = LoggerFactory.getLogger("App")

fun Route.innsendingRoute(postgres: PostgresRepo, redis: Redis) {
    route("/innsending") {

        post {
            val personIdent = call.personident()
            val innsending = call.receive<Innsending>()
            val innsendingId = UUID.randomUUID()
            logger.trace("Mottok innsending med id {}", innsendingId)

            val vedleggMedDataPairs = innsending.vedlegg.mapNotNull { vedlegg ->
                redis[vedlegg.id]?.let { Pair(vedlegg, it) }
            }

            if (vedleggMedDataPairs.size != innsending.vedlegg.size) {
                return@post call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret vedlegg")
            }

            postgres.lagreInnsending(
                innsendingId = innsendingId,
                personIdent = personIdent,
                mottattDato = LocalDateTime.now(),
                innsending = innsending,
                vedlegg = vedleggMedDataPairs
            )

            innsending.vedlegg.forEach { vedlegg ->
                redis.del(vedlegg.id)
            }
            redis.del(personIdent)

            call.respond(HttpStatusCode.OK, "Vi har mottatt innsendingen din")
        }
    }
}

data class Innsending(
    val soknad: ByteArray? = null,
    val vedlegg: List<Vedlegg>,
)

data class Vedlegg(
    val id: String,
    val tittel: String,
)
