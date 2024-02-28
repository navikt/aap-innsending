package innsending.routes

import innsending.SECURE_LOGGER
import innsending.antivirus.ClamAVClient
import innsending.auth.personident
import innsending.dto.ErrorRespons
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


private val SUPPORTED_TYPES =
    listOf(ContentType.Image.JPEG, ContentType.Image.PNG, ContentType.Application.Pdf)

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
                null -> call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret søknad")
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
                    SøknadFinnesRespons("aap-søknad", URI("https://www.nav.no/aap/soknad").toURL(), createdAt)
                )
            } else {
                call.respond(HttpStatusCode.NotFound, SøknadFinnesRespons())
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
                    HttpStatusCode.UnprocessableEntity,
                    ErrorRespons("Request was either form-data or missing its multipart-file.")
                )

            val fil = multipartFile.streamProvider().readBytes().also {
                if (it.isEmpty()) return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorRespons("Filen er tom")
                )
            }

            val contentType = multipartFile.contentType?.also {
                if (it.isNotSupported(fil)) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRespons("Filtype $it er ikke støttet. Filtypen må være en av $SUPPORTED_TYPES")
                    )
                }
            } ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                ErrorRespons("Content-Type i multipart fil mangler.")
            )

            if (virusScanClient.hasVirus(fil, contentType)) {
                SECURE_LOGGER.warn("Bruker prøvde å laste opp virus")
                return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorRespons("Fant virus i fil")
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
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRespons("PDF er kryptert"))
            }

            redis.set(key, pdf, EnDagSekunder)

            call.respond(status = HttpStatusCode.Created, MellomlagringRespons(key.value))
        }

        get("/{filId}") {
            val key = Key(
                value = requireNotNull(call.parameters["filId"]),
                prefix = call.personident()
            )

            when (val fil = redis[key]) {
                null -> call.respond(HttpStatusCode.NotFound, ErrorRespons("Fant ikke mellomlagret fil"))
                else -> {
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment
                            .withParameter(
                                ContentDisposition.Parameters.FileName,
                                "${key}.pdf" // TODO: tittel kan lagres på egen key:value
                            )
                            .toString()
                    )
                    call.respond(HttpStatusCode.OK, fil)
                }
            }
        }

        delete("/{filId}") {
            val key = Key(
                value = requireNotNull(call.parameters["filId"]),
                prefix = call.personident(),
            )

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
        ErrorRespons("Innsending has incorrectly setup its http client toward PdfGen. Check Innsending's logs.")
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.pdfGenReportsInternalError() {
    call.respond(
        HttpStatusCode.InternalServerError,
        ErrorRespons("PdfGen failed internally, check PdfGen's logs.")
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.pdfGenReportsIncorrectUsage() {
    call.respond(
        HttpStatusCode.InternalServerError,
        ErrorRespons("Innsending is using PdfGen wrong. Check Innsending's logs.")
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.failedToDeserialize() {
    call.respond(
        HttpStatusCode.InternalServerError,
        ErrorRespons("Innsending failed to deserialize (read) the response from PdfGen. Check Innsending's logs.")
    )
}

fun createdAt(ageInSeconds: Long): Date {
    return Date(System.currentTimeMillis() - ageInSeconds * 1000)
}

fun kryptertEllerUgyldigPdf(fil: ByteArray): Boolean {
    try {
        val pdf = Loader.loadPDF(fil)
        return pdf.isEncrypted
    } catch (e: Exception) {
        return true
    }
}

fun ContentType.isNotSupported(fil: ByteArray): Boolean =
    runCatching {
        val filtype = Tika().detect(fil)
        SECURE_LOGGER.info("sjekker filtype $filtype == $contentType")
        this !in SUPPORTED_TYPES || filtype != contentType
    }.getOrDefault(true)


data class SøknadFinnesRespons(
    val tittel: String? = null,
    val link: URL? = null,
    val sistEndret: LocalDateTime? = null
)
