package innsending.routes

import innsending.APP_LOG
import innsending.SECURE_LOG
import innsending.auth.personident
import innsending.dto.ErrorCode
import innsending.dto.Innsending
import innsending.dto.error
import innsending.postgres.PostgresRepo
import innsending.redis.Key
import innsending.redis.Redis
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime
import java.util.*

fun Route.innsendingRoute(postgres: PostgresRepo, redis: Redis) {

    /**
     * Innsending av søknad
     */
    route("/innsending") {

        /**
         * Hent alle søknader for innlogget bruker
         */
        get("/søknader") {
            val personIdent = call.personident()
            val søknader = postgres.hentAlleSøknader(personIdent)

            call.respond(
                HttpStatusCode.OK,
                søknader
            )
        }

        /**
         * Hent søknad for innlogget bruker
         */
        get("/søknader/{ref}/ettersendinger") {
            val ref = call.parameters["ref"]?.let(UUID::fromString)
                ?: return@get call.error(ErrorCode.REQ_MISSING_INNSENDING_REF)

            val søknadMedEttersendinger = postgres.hentSøknadMedEttersendelser(ref)
                ?: return@get call.error(ErrorCode.NOT_FOUND_SOKNAD)

            call.respond(
                HttpStatusCode.OK,
                søknadMedEttersendinger
            )
        }

        /**
         * Lagre innsending med referanse
         */
        post("/{ref}") {
            val ref = call.parameters["ref"]?.let(UUID::fromString)
                ?: return@post call.error(ErrorCode.REQ_MISSING_INNSENDING_REF)

            postInnsending(postgres, redis, call, ref)
        }

        /**
         * Lagre innsending
         */
        post {
            postInnsending(postgres, redis, call)
        }
    }
}

// todo: set expiry hver gang noe endres, også for vedlegg
private suspend fun postInnsending(
    postgres: PostgresRepo,
    redis: Redis,
    call: ApplicationCall,
    innsendingsRef: UUID? = null
) {
    val personIdent = call.personident()
    val innsending = call.receive<Innsending>()

    // Avoid duplicates
    val innsendingHash = Key(innsending.hashCode().toString())
    if (redis.exists(innsendingHash)) {
        call.error(ErrorCode.DUPLICATE_INNSENDING)
    }

    if (innsendingsRef != null && postgres.erRefTilknyttetPersonIdent(personIdent, innsendingsRef).not()) {
        SECURE_LOG.error("$personIdent prøver å poste en innsending på $innsendingsRef, men disse hører ikke sammen")
        return call.error(ErrorCode.NOT_FOUND_INNSENDING)
    }

    val innsendingId = UUID.randomUUID()
    APP_LOG.trace("Mottok innsending med id {}", innsendingId)

    val filerMedInnhold = innsending.filer
        .associateWith { fil ->
            redis[Key(value = fil.id, prefix = personIdent)]
                ?: return call.error(ErrorCode.NOT_FOUND_FILE)
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
        redis.del(Key(fil.id, personIdent))
    }

    redis.del(Key(personIdent))

    // Avoid duplicates
    redis.set(innsendingHash, byteArrayOf(), 60)

    call.respond(HttpStatusCode.Created)
}
