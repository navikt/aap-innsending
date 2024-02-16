package innsending.routes

import innsending.SECURE_LOGGER
import innsending.auth.personident
import innsending.dto.Innsending
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

        get("/søknader/{ref}/ettersendinger") {
            val innsendingsRef = call.parameters["ref"]?.let(UUID::fromString) ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                "Mangler innsendingsId"
            )

            val søknadMedEttersendinger = postgres.hentSøknadMedEttersendelser(innsendingsRef)

            if(søknadMedEttersendinger == null) {
                call.respond(HttpStatusCode.NotFound, "Fant ikke søknad for angitt referanse")
            } else {
                call.respond(søknadMedEttersendinger)
            }
        }

        post("/{ref}") {
            val innsendingsRef = call.parameters["ref"]?.let(UUID::fromString) ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                "Mangler innsendingsId"
            )

            postInnsending(postgres, redis, call, innsendingsRef)
        }

        post {
            postInnsending(postgres, redis, call)
        }
    }
}

private suspend fun postInnsending(postgres: PostgresRepo, redis: Redis, call: ApplicationCall, innsendingsRef: UUID? = null) {
    val personIdent = call.personident()
    val innsending = call.receive<Innsending>()

    // Avoid duplicates
    val innsendingHash = Key(innsending.hashCode().toString())
    if (redis.exists(innsendingHash)) {
        call.respond(HttpStatusCode.Conflict, "Denne innsendingen har vi allerede mottatt")
    }

    if (innsendingsRef != null && postgres.erRefTilknyttetPersonIdent(personIdent, innsendingsRef).not()) {
        SECURE_LOGGER.error("$personIdent prøver å poste en innsending på $innsendingsRef, men disse hører ikke sammen")
        return call.respond(
            HttpStatusCode.NotFound,
            "Denne innsendingenId'en finnes ikke for denne personen"
        )
    }

    val innsendingId = UUID.randomUUID()
    logger.trace("Mottok innsending med id {}", innsendingId)

    val filerMedInnhold = innsending.filer.associateWith {fil ->
        redis[Key(value = fil.id, prefix = personIdent)]
            ?: return call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret fil")
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
