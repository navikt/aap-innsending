package innsending.routes

import innsending.antivirus.ClamAVClient
import innsending.auth.personident
import innsending.dto.ErrorRespons
import innsending.dto.MellomlagringRespons
import innsending.pdf.PdfGen
import innsending.prometheus
import innsending.redis.EnDagSekunder
import innsending.redis.Key
import innsending.redis.Redis
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.io.EOFException
import org.apache.pdfbox.Loader
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import java.time.LocalDateTime
import java.util.*

private val log = LoggerFactory.getLogger("MellomLagringRoute")

private val acceptedContentType =
    listOf(ContentType.Image.JPEG, ContentType.Image.PNG, ContentType.Application.Pdf)


fun Route.mellomlagerRoute(
    redis: Redis,
    virusScanClient: ClamAVClient,
    pdfGen: PdfGen,
    maxFileSize: Int
) {
    val CONTENT_LENGHT_LIMIT = maxFileSize * 1024 * 1024 // 75 MB
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
                null -> call.respond(HttpStatusCode.NoContent, "Fant ikke mellomlagret søknad")
                else -> call.respond(HttpStatusCode.OK, soknad)
            }
        }

        get("/finnes") {
            val personIdent = Key(call.personident())
            val søknad = redis[personIdent]
            if (søknad != null) {
                val createdAt = redis.lastUpdated(personIdent)
                log.info("søknad created at: {}", createdAt) //TODO: fjern logg
                call.respond(
                    HttpStatusCode.OK,
                    SøknadFinnesRespons(
                        "Søknad om AAP",
                        URI("https://www.nav.no/aap/soknad").toURL(),
                        createdAt
                    )
                )
            } else {
                call.respond(HttpStatusCode.NoContent, SøknadFinnesRespons())
            }
        }

        delete {
            val key = Key(call.personident())
            redis.del(key)
            call.respond(HttpStatusCode.OK)
        }
    }
    route("/mellomlagring/søknad/v2") {
        post {
            val key = Key(call.personident())
            val vedleggString = call.request.headers["vedlegg"]
            redis.set(key, call.receive(), EnDagSekunder)

            vedleggString?.split(",")?.forEach { filId ->
                redis.setExpire(Key(prefix = call.personident(), value = filId), EnDagSekunder)
            }
            call.respond(HttpStatusCode.OK)
        }
    }

    route("/mellomlagring/fil") {
        post {
            val key = Key(
                value = UUID.randomUUID().toString(),
                prefix = call.personident()
            )

            log.info("Leser vedlegg fra request. Content-Type: ${call.request.contentType()}.")
            // Veldig høy maksgrense siden vi sjekker filtype manuelt
            val receiveMultipart =
                call.receiveMultipart(formFieldLimit = 1000 * CONTENT_LENGHT_LIMIT.toLong())

            log.info("Fikk til å lese multipart.")

            when (val fileItem = receiveMultipart.readPart()) {
                is PartData.FileItem -> {
                    val fil = fileItem
                        .readFile(CONTENT_LENGHT_LIMIT)
                        .getOrElse {
                            when (it) {
                                is EmptyStreamException -> return@post call.respond(
                                    HttpStatusCode.UnprocessableEntity,
                                    ErrorRespons("Filen er tom")
                                )

                                else -> {
                                    log.info("Got exc: {}", it.message)
                                    return@post call.respond(
                                        HttpStatusCode.UnprocessableEntity,
                                        ErrorRespons("Filen ${fileItem.originalFileName} er større enn maksgrense på ${maxFileSize}MB")
                                    )
                                }

                            }
                        }

                    prometheus.registrerVedleggStørrelse(fil.size.toLong())

                    val contentType = requireNotNull(fileItem.contentType) {
                        "contentType i multipartForm mangler"
                    }

                    if (sjekkFeilContentType(fil, contentType)) {
                        return@post call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorRespons("Filtype ikke støttet")
                        )
                    }

                    val pdf: ByteArray = when (contentType) {
                        in acceptedContentType -> {
                            if (virusScanClient.hasVirus(fil, contentType)) {
                                log.warn("Bruker prøvde å laste opp virus")
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
                                    log.error("Feil fra PDFgen", e)
                                    return@post call.respond(
                                        HttpStatusCode.UnprocessableEntity,
                                        ErrorRespons("Feil ved omgjøring til pdf")
                                    )
                                }
                            }
                        }

                        else -> {
                            log.warn("Feil filtype ${contentType.contentType}")
                            return@post call.respond(
                                HttpStatusCode.UnprocessableEntity,
                                ErrorRespons("Filtype ikke støttet")
                            )
                        }
                    }

                    if (kryptertEllerUgyldigPdf(pdf)) {
                        log.info("Fikk kryptert eller ugyldig PDF.")
                        return@post call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorRespons("PDF er kryptert")
                        )
                    }

                    // prefikser med innlogget bruker for å hindre at andre brukere kan hente andres filer
                    redis.set(key, pdf, EnDagSekunder)

                    call.respond(status = HttpStatusCode.Created, MellomlagringRespons(key.value))
                }

                else -> {
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorRespons("Filtype ikke støttet")
                    )
                }
            }
        }

        get("/{filId}") {
            val key = Key(
                value = requireNotNull(call.parameters["filId"]),
                prefix = call.personident()
            )

            when (val fil = redis[key]) {
                null -> call.respond(
                    HttpStatusCode.NotFound,
                    ErrorRespons("Fant ikke mellomlagret fil")
                )

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
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

class EmptyStreamException(msg: String) : RuntimeException(msg)

private suspend fun PartData.FileItem.readFile(fileSizeLimit: Int): Result<ByteArray> =
    runCatching {
        var buffer = ByteArray(1024)
        var idx = 0

        fun expandBuffer() {
            buffer = buffer.copyOf(buffer.size * 2)
            require(buffer.size <= fileSizeLimit) { "Filen er større enn tillat størrelse på $fileSizeLimit" }
        }

        while (provider().awaitContent()) {
            if (idx == buffer.size) expandBuffer()
            try {
                buffer[idx++] = provider().readByte()
            } catch (e: EOFException) {
                log.error("No bytes found in stream", e)
                throw EmptyStreamException("No bytes found in stream")
            } catch (e: Exception) {
                log.error("Feil ved lesing av fil", e)
                throw e
            }
        }

        buffer.copyOfRange(0, idx)
    }


fun kryptertEllerUgyldigPdf(fil: ByteArray): Boolean {
    try {
        val pdf = Loader.loadPDF(fil)
        if (pdf.isEncrypted) {
            log.info("Bruker sendte inn kryptert PDF.")
        }
        return pdf.isEncrypted
    } catch (e: Exception) {
        log.info("Bruker sendte inn ugyldig pdf.")
        return true
    }
}

fun sjekkFeilContentType(fil: ByteArray, contentType: ContentType): Boolean {
    val filtype = Tika().detect(fil)
    log.debug("sjekker filtype {} == {}", filtype, contentType)


    return filtype != contentType.toString()
}

data class SøknadFinnesRespons(
    val tittel: String? = null,
    val link: URL? = null,
    val sistEndret: LocalDateTime? = null
)
