package innsending.routes

import innsending.logger
import innsending.auth.personident
import innsending.dto.Innsending
import innsending.dto.InnsendingResponse
import innsending.dto.ValiderFiler
import innsending.postgres.PostgresRepo
import innsending.redis.EnDagSekunder
import innsending.redis.Key
import innsending.redis.Redis
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDateTime
import java.util.UUID

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

            if (søknadMedEttersendinger == null) {
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

        post("/valider-filer") {
            val personIdent = call.personident()
            val innsending = call.receive<ValiderFiler>()

            innsending.filer.forEach { fil ->
                redis.setExpire(Key(value = fil.id, prefix = personIdent), EnDagSekunder)
            }

            val filerMedInnhold = innsending.filer.associateWith { fil ->
                redis[Key(value = fil.id, prefix = personIdent)]
            }.toList()

            val manglendeFiler = filerMedInnhold.filter { it.second == null }.map { it.first }

            call.respond(HttpStatusCode.OK, manglendeFiler)
        }

        post {
            postInnsending(postgres, redis, call)
        }
    }
}

private suspend fun postInnsending(postgres: PostgresRepo,
                                   redis: Redis,
                                   call: ApplicationCall,
                                   innsendingsRef: UUID? = null) {
    val personIdent = call.personident()
    val innsending = call.receive<Innsending>()

    // Avoid duplicates
    val innsendingHash = Key(innsending.hashCode().toString())
    if (redis.exists(innsendingHash)) {
        call.respond(HttpStatusCode.Conflict, "Denne innsendingen har vi allerede mottatt")
    }

    if (innsendingsRef != null && postgres.erRefTilknyttetPersonIdent(personIdent, innsendingsRef).not()) {
        logger.error("$personIdent prøver å poste en innsending på $innsendingsRef, men disse hører ikke sammen")
        return call.respond(
            HttpStatusCode.NotFound,
            "Denne innsendingenId'en finnes ikke for denne personen"
        )
    }

    val innsendingId = UUID.randomUUID()
    logger.info("Mottok innsending med id {}", innsendingId)

    // Denne vil gi 404 ved innsending hvis det er usync mellom frontend og redis
    // Dermed blokkere innsending av søknad
    val filerMedInnhold = innsending.filer.associateWith { fil ->
        redis[Key(value = fil.id, prefix = personIdent)]
    }.toList()

    val manglendeFiler = filerMedInnhold.filter { it.second == null }.map { it.first }

    if (manglendeFiler.isNotEmpty()) {
        logger.warn("Mangler filer fra innsending med id={} :: {}", innsendingId, manglendeFiler.map { it.id })
        logger.warn("$personIdent Mangler filer fra innsending :: {}", manglendeFiler.map { it.id })
        return call.respond(HttpStatusCode.PreconditionFailed, manglendeFiler)
    }

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

    call.respond(HttpStatusCode.OK, InnsendingResponse(innsendingId))
}
