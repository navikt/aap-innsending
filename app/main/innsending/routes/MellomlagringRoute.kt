package innsending.routes

import innsending.antivirus.ClamAVClient
import innsending.auth.personident
import innsending.dto.ErrorCode
import innsending.dto.MellomlagringRespons
import innsending.dto.SøknadFinnesRespons
import innsending.dto.error
import innsending.http.ApiResult
import innsending.pdf.Pdf
import innsending.pdf.PdfGen
import innsending.redis.EnDagSekunder
import innsending.redis.Key
import innsending.redis.Redis
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.apache.pdfbox.Loader
import org.apache.tika.Tika
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

val SUPPORTED_TYPES = listOf(ContentType.Image.JPEG, ContentType.Image.PNG, ContentType.Application.Pdf)

fun Route.mellomlagerRoute(redis: Redis, virusScanClient: ClamAVClient, pdfGen: PdfGen) {

    /**
     * Mellomlagring av søknad
     */
    route("/mellomlagring/søknad") {

        /**
         * Lagre søknad for inlogget bruker. Slettes etter 1 dag
         */
        post {
            val key = Key(call.personident())
            redis.set(key, call.receive(), EnDagSekunder)
            call.respond(HttpStatusCode.Created)
        }

        /**
         * Hent søknad for innlogget bruker
         */
        get {
            val key = Key(call.personident())
            when (val soknad = redis[key]) {
                null -> call.error(ErrorCode.NOT_FOUND_SOKNAD)
                else -> call.respond(HttpStatusCode.OK, soknad)
            }
        }

        /**
         * Hent søknad med link til søknadsskjema hvis den finnes
         */
        get("/finnes") {
            val personIdent = Key(call.personident())
            val søknad = redis[personIdent]

            fun finnes(): SøknadFinnesRespons {
                val age = redis.createdAt(personIdent)
                return SøknadFinnesRespons(
                    tittel = "aap-søknad",
                    link = URI("https://www.nav.no/aap/soknad").toURL(),
                    sistEndret = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(age),
                        TimeZone.getDefault().toZoneId()
                    )
                )
            }

            when (søknad) {
                // todo: kan vi bytte til call.error(ErrorCode.NOT_FOUND_SOKNAD)?
                null -> call.respond(HttpStatusCode.NotFound, "{}")
                else -> call.respond(HttpStatusCode.OK, finnes())
            }
        }

        /**
         * Slett søknad for innlogget bruker
         */
        delete {
            val key = Key(call.personident())
            redis.del(key)
            call.respond(HttpStatusCode.OK)
        }
    }

    /**
     * Mellomlagring av fil(er)
     */
    route("/mellomlagring/fil") {

        /**
         * Lagre fil for inlogget bruker. Slettes etter 1 dag
         */
        post {
            val key = Key(
                value = UUID.randomUUID().toString(),
                prefix = call.personident()
            )

            val multipartFile = multipartFileOrNull()
                ?: return@post call.error(ErrorCode.REQ_MISSING_MULTIPART_FILE)

            val fil = multipartFile.streamProvider().readBytes()

            if (fil.isEmpty()) {
                return@post call.error(ErrorCode.UNPROC_EMPTY_FILE)
            }

            val contentType = multipartFile.contentType
                ?: return@post call.error(ErrorCode.REQ_MISSING_CONTENT_TYPE)

            if (contentType.isNotSupported(fil)) {
                return@post call.error(ErrorCode.REQ_WRONG_CONTENT_TYPE)
            }

            if (virusScanClient.hasVirus(fil, contentType)) {
                return@post call.error(ErrorCode.UNPROC_VIRUS)
            }

            val pdf = when (contentType) {
                ContentType.Application.Pdf -> fil
                else -> {
                    when (val result = pdfGen.bildeTilPfd(fil, contentType)) {
                        is ApiResult.Ok -> result.getOrNull<Pdf>()
                            ?: return@post call.error(ErrorCode.PDFGEN_DESERIALIZE_ERR)

                        is ApiResult.ClientError -> result.getNullAndTrace()
                            ?: return@post call.error(ErrorCode.PDFGEN_USAGE_ERR)

                        is ApiResult.ServerError -> result.getNullAndTrace()
                            ?: return@post call.error(ErrorCode.PDFGEN_INTERNAL_ERR)

                        is ApiResult.UnknownError -> result.getNullAndTrace()
                            ?: return@post call.error(ErrorCode.UNKNOWN_ERROR)
                    }
                }
            }

            if (encryptedOrInvalidPDF(pdf)) {
                return@post call.error(ErrorCode.UNPROC_ENCRYPTED_PDF)
            }

            redis.set(key, pdf, EnDagSekunder)

            call.respond(
                HttpStatusCode.Created,
                MellomlagringRespons(key.value)
            )
        }

        /**
         * Hent fil for innlogget bruker
         */
        get("/{filId}") {
            val key = Key(
                value = requireNotNull(call.parameters["filId"]),
                prefix = call.personident()
            )

            suspend fun ApplicationCall.respondWithDisposition(fil: ByteArray) {
                response.header(
                    name = HttpHeaders.ContentDisposition,
                    value = ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, "${key}.pdf")
                        .toString()
                )

                respond(
                    HttpStatusCode.OK,
                    fil
                )
            }

            when (val fil = redis[key]) {
                null -> call.error(ErrorCode.NOT_FOUND_FILE)
                else -> call.respondWithDisposition(fil)
            }
        }

        /**
         * Slett fil for innlogget bruker
         */
        delete("/{filId}") {
            val filId = call.parameters["filId"]
                ?: return@delete call.error(ErrorCode.REQ_MISSING_FILID)

            val key = Key(filId, call.personident())
            redis.del(key)
            call.respond(HttpStatusCode.OK)
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.multipartFileOrNull() =
    call.receiveMultipart()
        .readAllParts()
        .singleOrNull() as? PartData.FileItem

fun encryptedOrInvalidPDF(fil: ByteArray): Boolean {
    return runCatching {
        val pdf = Loader.loadPDF(fil)
        pdf.isEncrypted
    }.getOrDefault(true)
}

fun ContentType.isSupported(fil: ByteArray): Boolean {
    return runCatching {
        val filtype = Tika().detect(fil)
        this in SUPPORTED_TYPES && filtype == this.toString()
    }.getOrDefault(false)
}

fun ContentType.isNotSupported(fil: ByteArray): Boolean {
    return !isSupported(fil)
}
