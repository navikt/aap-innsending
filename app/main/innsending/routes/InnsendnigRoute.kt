package innsending.routes

import innsending.auth.personident
import innsending.postgres.PostgresRepo
import innsending.redis.Key
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

        get("/søknader") {
            val personIdent = call.personident()

            call.respond(postgres.hentAlleSøknader(personIdent))
        }
        post("/{ref}") {
            val personIdent = call.personident()
            val innsending = call.receive<Innsending>()

            // Avoid duplicates
            val innsendingHash = Key(innsending.hashCode().toString())
            if (redis.exists(innsendingHash)) {
                call.respond(HttpStatusCode.Conflict, "Denne innsendingen har vi allerede mottatt")
            }

            val innsendingsRef = call.parameters["ref"]?.let(UUID::fromString) ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                "Mangler innsendingsId"
            )
            if (postgres.erRefTilknyttetPersonIdent(personIdent, innsendingsRef).not()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Denne innsendingenId'en finnes ikke for denne personen"
                )
            }

            val innsendingId = UUID.randomUUID()
            logger.trace("Mottok innsending med id {}", innsendingId)

            val filerMedInnhold = innsending.filer.associateWith {fil ->
                redis[Key(value = fil.id, prefix = personIdent)]
                    ?: return@post call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret fil")
            }.toList()

            postgres.lagreInnsending(
                innsendingId = innsendingId,
                personIdent = personIdent,
                mottattDato = LocalDateTime.now(),
                innsending = innsending,
                fil = filerMedInnhold,
                referanseId = innsendingsRef
            )

            innsending.filer.forEach { fil ->
                val key = Key(value = fil.id, prefix = personIdent)
                redis.del(key)
            }

            redis.del(Key(personIdent))

            // Avoid duplicates
            redis.set(innsendingHash, byteArrayOf(), 60)

            call.respond(HttpStatusCode.OK, "Vi har mottatt innsendingen din")


        }

        post {
            val personIdent = call.personident()
            val innsending = call.receive<Innsending>()

            // Avoid duplicates
            val innsendingHash = Key(innsending.hashCode().toString())
            if (redis.exists(innsendingHash)) {
                call.respond(HttpStatusCode.Conflict, "Denne innsendingen har vi allerede mottatt")
            }

            val innsendingId = UUID.randomUUID()
            logger.trace("Mottok innsending med id {}", innsendingId)

            val filerMedDataPairs = innsending.filer.mapNotNull { fil ->
                val key = Key(value = fil.id, prefix = personIdent)
                redis[key]?.let { bytes -> fil to bytes }
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
                val key = Key(value = fil.id, prefix = personIdent)
                redis.del(key)
            }


            redis.del(Key(personIdent))

            // Avoid duplicates
            redis.set(innsendingHash, byteArrayOf(), 60)

            call.respond(HttpStatusCode.OK, "Vi har mottatt innsendingen din")
        }
    }
}

data class Logg(
    val journalpost: String,
    val mottattDato: LocalDateTime,
    val innsendingsId: UUID
)

data class Innsending(
    /**
     * Kvittering er JSON for å produsere kvitterings pdf til bruker
     */
    val kvittering: Map<String, Any>? = null,
    /*
     * soknad er søknad i JSON for å lagre orginalsøknad i joark
     */
    val soknad: Map<String, Any>? = null,
    /*
     * Filer er vedlegg til søknad ELLER Generell ettersendelse
     */
    val filer: List<FilMetadata>,
) {
    init {
        if ((soknad == null && kvittering != null) || (soknad != null && kvittering == null)) {
            throw IllegalArgumentException("Kvittering og søknad må være satt samtidig")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Innsending) return false

        if (kvittering != other.kvittering) return false
        if (filer != other.filer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kvittering?.hashCode() ?: 0
        result = 31 * result + filer.hashCode()
        return result
    }
}

data class FilMetadata(
    val id: String,
    val tittel: String,
)

data class MineAapSoknad(
    val mottattDato: LocalDateTime,
    val journalpostId: String?,
    val innsendingsId: UUID
)
