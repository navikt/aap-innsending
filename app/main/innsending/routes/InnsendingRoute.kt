package innsending.routes

import com.fasterxml.jackson.databind.ObjectMapper
import innsending.auth.personident
import innsending.db.FilNy
import innsending.db.InMemoryFilData
import innsending.db.InnsendingNy
import innsending.db.InnsendingRepo
import innsending.dto.*
import innsending.jobb.ArkiverInnsendingJobbUtfører
import innsending.logger
import innsending.redis.EnDagSekunder
import innsending.redis.Key
import innsending.redis.Redis
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.dokumenter.Søknad
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

fun Route.innsendingRoute(dataSource: DataSource, redis: Redis, promethius: MeterRegistry, maxFileSize: Int) {
    route("/innsending") {

        get("/søknadmedettersendinger") {
            val personIdent = call.personident()
            val res = dataSource.transaction(readOnly = true) { dbconnection ->
                val innsendingRepo = InnsendingRepo(dbconnection)
                innsendingRepo.hentAlleSøknader(personIdent)
            }
            call.respond(res)
        }

        get("/søknader") {
            val personIdent = call.personident()
            val res = dataSource.transaction(readOnly = true) { dbconnection ->
                val innsendingRepo = InnsendingRepo(dbconnection)
                innsendingRepo.hentAlleSøknader(personIdent)
            }
            call.respond(res)
        }

        get("/søknader/{ref}/ettersendinger") {
            val innsendingsRef = call.parameters["ref"]?.let(UUID::fromString) ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                "Mangler innsendingsId"
            )

            val søknadMedEttersendinger = dataSource.transaction(readOnly = true) { dbconnection ->
                val innsendingRepo = InnsendingRepo(dbconnection)
                innsendingRepo.hentSøknadMedReferanse(innsendingsRef)
            }

            if (søknadMedEttersendinger == null) {
                call.respond(HttpStatusCode.NotFound, "Fant ikke søknad for angitt referanse")
            } else {
                val response = MineAapSoknadMedEttersendinger(
                    mottattDato = søknadMedEttersendinger.mottattDato,
                    journalpostId = søknadMedEttersendinger.journalpostId,
                    innsendingsId = innsendingsRef,
                    ettersendinger = søknadMedEttersendinger.ettersendinger.map { ny ->
                        MineAapEttersending(
                            mottattDato = ny.mottattDato,
                            journalpostId = ny.journalpostId,
                            innsendingsId = ny.innsendingsId
                        )
                    })
                call.respond(response)
            }
        }

        post("/{ref}") {
            val innsendingsRef = UUID.fromString(call.parameters["ref"]) ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                "Mangler innsendingsId"
            )

            postInnsending(dataSource, redis, call, innsendingsRef, promethius, maxFileSize)
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
            postInnsending(dataSource, redis, call, null, promethius, maxFileSize)
        }
    }
}

private suspend fun postInnsending(
    dataSource: DataSource,
    redis: Redis,
    call: ApplicationCall,
    innsendingsRef: UUID? = null,
    prometheus: MeterRegistry,
    maxFileSize: Int
) {
    val CONTENT_LENGHT_LIMIT = maxFileSize * 1024 * 1024
    val personIdent = call.personident()
    val innsending = call.receive<Innsending>()

    // Avoid duplicates
    val innsendingHash = Key(innsending.hashCode().toString())
    if (redis.exists(innsendingHash)) {
        return call.respond(HttpStatusCode.Conflict, "Denne innsendingen har vi allerede mottatt")
    }

    val erRefTilknyttetPersonIdent = dataSource.transaction(readOnly = true) { dbconnection ->
        val innsendingRepo = InnsendingRepo(dbconnection)
        if (innsendingsRef == null) {
            true
        } else
            innsendingRepo.erRefTilknyttetPersonIdent(personIdent, innsendingsRef).not()
    }

    if (innsendingsRef != null && erRefTilknyttetPersonIdent) {
        logger.error("$personIdent prøver å poste en innsending på $innsendingsRef, men disse hører ikke sammen")
        return call.respond(
            HttpStatusCode.BadRequest,
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
        return call.respond(HttpStatusCode.PreconditionFailed, manglendeFiler)
    }

    val totalSize = filerMedInnhold.sumOf { it.second?.size ?: 0 }
    if (totalSize > CONTENT_LENGHT_LIMIT) {
        logger.info(
            "Vedleggenes totale størrelse overskrider maks på 50mb({}), totalt {}",
            CONTENT_LENGHT_LIMIT,
            totalSize
        )
        return call.respond(
            HttpStatusCode.PreconditionFailed,
            "Total vedleggstørrelse overskrider maks på 50 mb."
        )
    }

    if (innsending.soknad != null) {
        validerSøknad(innsending.soknad)
    }

    dataSource.transaction { dbconnection ->
        val innsendingRepo = InnsendingRepo(dbconnection)
        val innsendingId = innsendingRepo.lagre(
            InnsendingNy(
                null,
                opprettet = LocalDateTime.now(),
                personident = personIdent,
                soknad = innsending.soknad?.toByteArray(),
                data = innsending.kvittering?.toByteArray(),
                eksternRef = innsendingId,
                type = innsending.type,
                forrigeInnsendingId = innsendingsRef?.let { _ -> innsendingRepo.hentIdFraEksternRef(innsendingsRef) },
                journalpost_Id = null,
                filer = filerMedInnhold.map { (metadata, byteArray) ->
                    FilNy(
                        tittel = metadata.tittel,
                        data = InMemoryFilData(requireNotNull(byteArray))
                    )
                }.toList()
            )
        )
        FlytJobbRepository(connection = dbconnection).leggTil(
            JobbInput(ArkiverInnsendingJobbUtfører).forSak(
                innsendingId
            )
        )
    }
    prometheus.counter("innsendinger").increment()

    innsending.filer.forEach { fil ->
        val key = Key(value = fil.id, prefix = personIdent)
        redis.del(key)
    }

    redis.del(Key(personIdent))

    // Avoid duplicates
    redis.set(innsendingHash, byteArrayOf(), 60)

    call.respond(HttpStatusCode.OK, InnsendingResponse(innsendingId))
}


fun validerSøknad(søknad: Map<String, Any>) {
    try {
        DefaultJsonMapper.fromJson<Søknad>(DefaultJsonMapper.toJson(søknad))
        logger.info("Søknad validerte ok.")
    } catch (e: Exception) {
        logger.warn("Feil ved validering av søknad.", e)
    }
}

fun Map<String, Any>.toByteArray(): ByteArray {
    val mapper = ObjectMapper()
    return mapper.writeValueAsBytes(this)
}
