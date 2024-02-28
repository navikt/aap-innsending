package innsending.routes

import innsending.antivirus.ClamAVClient
import innsending.auth.personident
import innsending.dto.ApiError
import innsending.dto.ErrorCode
import innsending.dto.MellomlagringRespons
import innsending.http.HttpResult
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
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

val SUPPORTED_TYPES = listOf(ContentType.Image.JPEG, ContentType.Image.PNG, ContentType.Application.Pdf)

fun Route.mellomlagerRoute(redis: Redis, virusScanClient: ClamAVClient, pdfGen: PdfGen) {
    route("/mellomlagring/søknad") {

        post {
            val key = Key(call.personident())
            redis.set(key, call.receive(), EnDagSekunder)
            call.respond(HttpStatusCode.OK)
        }

        get {
            val key = Key(call.personident())
            when (val soknad = redis[key]) {
                null -> call.respond(HttpStatusCode.NotFound, ApiError(ErrorCode.NOT_FOUND_SOKNAD))
                else -> call.respond(HttpStatusCode.OK, soknad)
            }
        }

        get("/finnes") {
            val personIdent = Key(call.personident())
            val søknad = redis[personIdent]

            if (søknad != null) {
                val age = redis.createdAt(personIdent)
                val createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(age), TimeZone.getDefault().toZoneId())
                call.respond(
                    HttpStatusCode.OK,
                    SøknadFinnesRespons(
                        tittel = "aap-søknad",
                        link = URI("https://www.nav.no/aap/soknad").toURL(),
                        sistEndret = createdAt
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    SøknadFinnesRespons()
                )
            }
        }

        delete {
            val key = Key(call.personident())
            redis.del(key)
            call.respond(HttpStatusCode.OK)
        }
    }

    route("/mellomlagring/fil") {
        post {
            val key = Key(
                value = UUID.randomUUID().toString(),
                prefix = call.personident()
            )

            val multipartFile = multipartFileOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(ErrorCode.REQ_MISSING_MULTIPART_FILE)
                )

            val fil = multipartFile.streamProvider().readBytes().also {
                if (it.isEmpty()) return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError(ErrorCode.UNPROC_EMPTY_FILE)
                )
            }

            val contentType = multipartFile.contentType?.also {
                if (it.isNotSupported(fil)) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(ErrorCode.REQ_WRONG_CONTENT_TYPE)
                    )
                }
            } ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiError(ErrorCode.REQ_MISSING_CONTENT_TYPE)
            )

            if (virusScanClient.hasVirus(fil, contentType)) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError(ErrorCode.UNPROC_VIRUS)
                )
            }

            val pdf = when (contentType) {
                ContentType.Application.Pdf -> fil
                else -> when (val res = pdfGen.bildeTilPfd(fil, contentType)) {
                    is HttpResult.Ok -> res.getOrNull() ?: return@post failedToDeserialize()
                    is HttpResult.ClientError -> res.traceError() ?: return@post pdfGenReportsIncorrectUsage()
                    is HttpResult.ServerError -> res.traceError() ?: return@post pdfGenReportsInternalError()
                    null -> return@post failedToSetupPdfClient()
                }
            }

            if (kryptertEllerUgyldigPdf(pdf)) {
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError(ErrorCode.UNPROC_ENCRYPTED_PDF)
                )
            }

            redis.set(key, pdf, EnDagSekunder)

            call.respond(
                HttpStatusCode.Created,
                MellomlagringRespons(key.value)
            )
        }

        get("/{filId}") {
            val key = Key(
                value = requireNotNull(call.parameters["filId"]),
                prefix = call.personident()
            )

            when (val fil = redis[key]) {
                null -> call.respond(
                    HttpStatusCode.NotFound,
                    ApiError(ErrorCode.NOT_FOUND_FILE)
                )

                else -> {
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment
                            .withParameter(ContentDisposition.Parameters.FileName, "${key}.pdf")
                            .toString()
                    )

                    call.respond(
                        HttpStatusCode.OK,
                        fil
                    )
                }
            }
        }

        delete("/{filId}") {
            val filId = call.parameters["filId"]
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError(ErrorCode.REQ_MISSING_FILID)
                )

            val key = Key(filId, call.personident())
            redis.del(key)
            call.respond(HttpStatusCode.OK)
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.multipartFileOrNull(): PartData.FileItem? =
    call.receiveMultipart().readAllParts().singleOrNull() as? PartData.FileItem

private suspend fun PipelineContext<Unit, ApplicationCall>.failedToSetupPdfClient() {
    call.respond(
        HttpStatusCode.InternalServerError,
        ApiError(ErrorCode.CLIENT_SETUP_ERR)
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.pdfGenReportsInternalError() {
    call.respond(
        HttpStatusCode.InternalServerError,
        ApiError(ErrorCode.CLIENT_INTERNAL_ERR)
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.pdfGenReportsIncorrectUsage() {
    call.respond(
        HttpStatusCode.InternalServerError,
        ApiError(ErrorCode.CLIENT_USAGE_ERR)
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.failedToDeserialize() {
    call.respond(
        HttpStatusCode.InternalServerError,
        ApiError(ErrorCode.DESERIALIZE_ERR)
    )
}

fun kryptertEllerUgyldigPdf(fil: ByteArray): Boolean {
    try {
        val pdf = Loader.loadPDF(fil)
        return pdf.isEncrypted
    } catch (e: Exception) {
        return true
    }
}

fun ContentType.isSupported(fil: ByteArray): Boolean =
    runCatching {
        val filtype = Tika().detect(fil)
        this in SUPPORTED_TYPES && filtype == this.toString()
    }.getOrDefault(false)

fun ContentType.isNotSupported(fil: ByteArray): Boolean = !isSupported(fil)

data class SøknadFinnesRespons(
    val tittel: String? = null,
    val link: URL? = null,
    val sistEndret: LocalDateTime? = null
)
