package innsending.routes

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.util.JSONPObject
import innsending.auth.personident
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

            // Avoid duplicates
            val innsendingHash = innsending.hashCode().toString()
            if (redis.exists(innsendingHash)) {
                call.respond(HttpStatusCode.Conflict, "Denne innsendingen har vi allerede mottatt")
            }

            val innsendingId = UUID.randomUUID()
            logger.trace("Mottok innsending med id {}", innsendingId)

            val filerMedDataPairs = innsending.filer.mapNotNull { fil ->
                redis[fil.id]?.let { Pair(fil, it) }
            }

            if (filerMedDataPairs.size != innsending.filer.size) {
                return@post call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret fil")
            }

            postgres.lagreInnsending(
                innsendingId = innsendingId,
                personIdent = personIdent,
                mottattDato = LocalDateTime.now(),
                innsending = innsending,
                fil = filerMedDataPairs
            )

            innsending.filer.forEach { fil ->
                redis.del(fil.id)
            }

            redis.del(personIdent)

            // Avoid duplicates
            redis.set(innsendingHash, byteArrayOf(), 60)

            call.respond(HttpStatusCode.OK, "Vi har mottatt innsendingen din")
        }
    }
}

data class Innsending(
    val kvittering: String? = null,
    val filer: List<Fil>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Innsending

        if (soknad != null) {
            if (other.soknad == null) return false
            if (!soknad.contentEquals(other.soknad)) return false
        } else if (other.soknad != null) return false
        if (filer != other.filer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = soknad?.contentHashCode() ?: 0
        result = 31 * result + filer.hashCode()
        return result
    }
}

data class Fil(
    val id: String,
    val tittel: String,
)
