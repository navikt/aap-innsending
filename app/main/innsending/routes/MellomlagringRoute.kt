package innsending.routes

import innsending.SECURE_LOGGER
import innsending.antivirus.ClamAVClient
import innsending.auth.personident
import innsending.dto.ErrorRespons
import innsending.dto.MellomlagringRespons
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
import org.apache.pdfbox.Loader
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

private val log = LoggerFactory.getLogger("App")

private val acceptedContentType =
    listOf(ContentType.Image.JPEG, ContentType.Image.PNG, ContentType.Application.Pdf)

const val CONTENT_LENGHT_LIMIT = 100 * 1024 * 1024 // 100 MB

fun Route.mellomlagerRoute(redis: Redis, virusScanClient: ClamAVClient, pdfGen: PdfGen) {
    route("/mellomlagring/søknad") {

        post {
            val key = Key(call.personident())
            redis.set(key, call.receive(), EnDagSekunder)
            redis.getKeysByPrefix(call.personident()).forEach { filKey ->
                redis.setExpire(filKey, EnDagSekunder)
            }
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
                val age = redis.lastUpdated(personIdent)
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

            when (val mottattFil = call.receiveMultipart().readAllParts().single()) {
                is PartData.FileItem -> {
                    val stream = mottattFil.streamProvider()

                    val contentLength = mottattFil.headers[HttpHeaders.ContentLength]?.toInt()
                        ?: stream.available().also { estimate ->
                            log.info(
                                """
                                Missing Content-Length header in multipart.
                                Will use estimated bytes ($estimate).
                            """.trimIndent()
                            )
                        }

                    if (contentLength > CONTENT_LENGHT_LIMIT) {
                        log.warn("Filen er for stor. Maks størrelse er 100 MB")
//                        return@post call.respond(
//                            HttpStatusCode.UnprocessableEntity,
//                            ErrorRespons("Filen er for stor. Maks størrelse er 100 MB")
//                        )
                    }

                    val fil = stream.readBytes()
                    val contentType = requireNotNull(mottattFil.contentType) { "contentType i multipartForm mangler" }

                    if (fil.isEmpty() || sjekkFeilContentType(fil, contentType)) {
                        return@post call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorRespons("Filtype ikke støttet")
                        )
                    }

                    val pdf: ByteArray = when (contentType) {
                        in acceptedContentType -> {
                            if (virusScanClient.hasVirus(fil, contentType)) {
                                SECURE_LOGGER.warn("Bruker prøvde å laste opp virus")
                                return@post call.respond(
                                    HttpStatusCode.UnprocessableEntity,
                                    ErrorRespons("Fant virus i fil")
                                )
                            }
                            if (contentType == ContentType.Application.Pdf) {
                                fil
                            } else {
                                try {
                                    pdfGen.bildeTilPfd(fil, contentType)
                                } catch (e: Exception) {
                                    SECURE_LOGGER.error("Feil fra PDFgen", e)
                                    return@post call.respond(
                                        HttpStatusCode.UnprocessableEntity,
                                        ErrorRespons("Feil ved omgjøring til pdf")
                                    )
                                }
                            }
                        }

                        else -> {
                            SECURE_LOGGER.warn("Feil filtype ${contentType.contentType}")
                            return@post call.respond(
                                HttpStatusCode.UnprocessableEntity,
                                ErrorRespons("Filtype ikke støttet")
                            )
                        }
                    }

                    if (kryptertEllerUgyldigPdf(pdf)) {
                        return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRespons("PDF er kryptert"))
                    }

                    // prefikser med innlogget bruker for å hindre at andre brukere kan hente andres filer
                    redis.set(key, pdf, EnDagSekunder)

                    call.respond(status = HttpStatusCode.Created, MellomlagringRespons(key.value))
                }

                else -> {
                    return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRespons("Filtype ikke støttet"))
                }
            }
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

fun sjekkFeilContentType(fil: ByteArray, contentType: ContentType): Boolean {
    val filtype = Tika().detect(fil)
    SECURE_LOGGER.debug("sjekker filtype {} == {}", filtype, contentType)


    return filtype != contentType.toString()
}

data class SøknadFinnesRespons(
    val tittel: String? = null,
    val link: URL? = null,
    val sistEndret: LocalDateTime? = null
)
