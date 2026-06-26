package innsending.routes

import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.delete
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
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

data class FilIdParam(@PathParam("filId") val filId: String)

fun NormalOpenAPIRoute.mellomlagerRoute(
    redis: Redis,
    virusScanClient: ClamAVClient,
    pdfGen: PdfGen,
    maxFileSize: Int
) {
    val CONTENT_LENGHT_LIMIT = maxFileSize * 1024 * 1024 // 75 MB
    route("/mellomlagring/søknad") {

        post<Unit, Unit, Unit> { _, _ ->
            val key = Key(pipeline.call.personident())
            redis.set(key, pipeline.call.receive(), EnDagSekunder)
            redis.getKeysByPrefix(pipeline.call.personident()).forEach { filKey ->
                redis.setExpire(filKey, EnDagSekunder)
            }
            pipeline.call.respond(HttpStatusCode.OK)
        }

        get<Unit, Unit> { _ ->
            val key = Key(pipeline.call.personident())
            when (val soknad = redis[key]) {
                null -> pipeline.call.respond(HttpStatusCode.NoContent, "Fant ikke mellomlagret søknad")
                else -> pipeline.call.respond(HttpStatusCode.OK, soknad)
            }
        }

        route("/finnes") {
            get<Unit, SøknadFinnesRespons> { _ ->
                val personIdent = Key(pipeline.call.personident())
                val søknad = redis[personIdent]
                if (søknad != null) {
                    val createdAt = redis.lastUpdated(personIdent)
                    log.info("søknad created at: {}", createdAt) //TODO: fjern logg
                    respond(
                        SøknadFinnesRespons(
                            "Søknad om AAP",
                            URI("https://www.nav.no/aap/soknad").toURL(),
                            createdAt
                        )
                    )
                } else {
                    pipeline.call.respond(HttpStatusCode.NoContent, SøknadFinnesRespons())
                }
            }
        }

        delete<Unit, Unit> { _ ->
            val key = Key(pipeline.call.personident())
            redis.del(key)
            pipeline.call.respond(HttpStatusCode.OK)
        }
    }

    route("/mellomlagring/søknad/v2") {
        post<Unit, Unit, Unit> { _, _ ->
            val key = Key(pipeline.call.personident())
            val vedleggString = pipeline.call.request.headers["vedlegg"]
            redis.set(key, pipeline.call.receive(), EnDagSekunder)

            vedleggString?.split(",")?.forEach { filId ->
                redis.setExpire(Key(prefix = pipeline.call.personident(), value = filId), EnDagSekunder)
            }
            pipeline.call.respond(HttpStatusCode.OK)
        }
    }

    route("/mellomlagring/fil") {
        post<Unit, MellomlagringRespons, Unit> { _, _ ->
            val key = Key(
                value = UUID.randomUUID().toString(),
                prefix = pipeline.call.personident()
            )

            log.info("Leser vedlegg fra request. Content-Type: ${pipeline.call.request.contentType()}.")
            val receiveMultipart =
                pipeline.call.receiveMultipart(formFieldLimit = 1000 * CONTENT_LENGHT_LIMIT.toLong())

            log.info("Fikk til å lese multipart.")

            val fileItem = try {
                receiveMultipart.readPart()
            } catch (e: Exception) {
                log.error("Feil ved lesing av multipart", e)
                return@post pipeline.call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorRespons("Feil ved lesing av vedlegg.")
                )
            }

            when (fileItem) {
                is PartData.FileItem -> {
                    val fil = fileItem
                        .readFile(CONTENT_LENGHT_LIMIT)
                        .getOrElse {
                            when (it) {
                                is EmptyStreamException -> return@post pipeline.call.respond(
                                    HttpStatusCode.UnprocessableEntity,
                                    ErrorRespons("Filen er tom")
                                )

                                else -> {
                                    log.info("Got exc: {}", it.message)
                                    return@post pipeline.call.respond(
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
                        return@post pipeline.call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorRespons("Filtype ikke støttet")
                        )
                    }

                    val pdf: ByteArray = when (contentType) {
                        in acceptedContentType -> {
                            log.info("Scanner vedlegg for virus.")
                            if (virusScanClient.hasVirus(fil, contentType)) {
                                log.warn("Bruker prøvde å laste opp virus")
                                return@post pipeline.call.respond(
                                    HttpStatusCode.UnprocessableEntity,
                                    ErrorRespons("Fant virus i fil")
                                )
                            }
                            if (contentType == ContentType.Application.Pdf) {
                                fil
                            } else {
                                log.info("Fil er ikke PDF. Konverterer til PDF.")
                                try {
                                    pdfGen.bildeTilPfd(fil, contentType)
                                } catch (e: Exception) {
                                    log.error("Feil fra PDFgen", e)
                                    return@post pipeline.call.respond(
                                        HttpStatusCode.UnprocessableEntity,
                                        ErrorRespons("Feil ved omgjøring til pdf")
                                    )
                                }
                            }
                        }

                        else -> {
                            log.warn("Feil filtype ${contentType.contentType}")
                            return@post pipeline.call.respond(
                                HttpStatusCode.UnprocessableEntity,
                                ErrorRespons("Filtype ikke støttet")
                            )
                        }
                    }

                    log.info("Sjekker om PDF er kryptert eller ugyldig.")
                    if (kryptertEllerUgyldigPdf(pdf)) {
                        log.info("Fikk kryptert eller ugyldig PDF.")
                        return@post pipeline.call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorRespons("PDF er kryptert")
                        )
                    }

                    redis.set(key, pdf, EnDagSekunder)

                    pipeline.call.respond(HttpStatusCode.Created, MellomlagringRespons(key.value))
                }

                else -> {
                    return@post pipeline.call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorRespons("Filtype ikke støttet")
                    )
                }
            }
        }

        route("/{filId}") {
            get<FilIdParam, Unit> { params ->
                val key = Key(
                    value = params.filId,
                    prefix = pipeline.call.personident()
                )

                when (val fil = redis[key]) {
                    null -> pipeline.call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRespons("Fant ikke mellomlagret fil")
                    )

                    else -> {
                        pipeline.call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment
                                .withParameter(
                                    ContentDisposition.Parameters.FileName,
                                    "${key}.pdf" // TODO: tittel kan lagres på egen key:value
                                )
                                .toString()
                        )
                        pipeline.call.respondBytes(fil, ContentType.Application.Pdf)
                    }
                }
            }

            delete<FilIdParam, Unit> { params ->
                val key = Key(
                    value = params.filId,
                    prefix = pipeline.call.personident(),
                )

                redis.del(key)
                pipeline.call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

class EmptyStreamException(msg: String) : RuntimeException(msg)

private suspend fun PartData.FileItem.readFile(fileSizeLimit: Int): Result<ByteArray> =
    runCatching {
        val channel = provider()
        var buffer = ByteArray(1024)
        var idx = 0

        fun expandBuffer() {
            buffer = buffer.copyOf(buffer.size * 2)
            require(buffer.size <= fileSizeLimit) { "Filen er større enn tillat størrelse på $fileSizeLimit" }
        }

        while (channel.awaitContent()) {
            if (idx == buffer.size) expandBuffer()
            try {
                buffer[idx++] = channel.readByte()
            } catch (e: EOFException) {
                log.error("No bytes found in stream", e)
                throw EmptyStreamException("No bytes found in stream")
            } catch (e: Exception) {
                log.error("Feil ved lesing av fil", e)
                throw e
            }
        }

        if (idx == 0) throw EmptyStreamException("No bytes found in stream")

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
        log.info("Bruker sendte inn ugyldig pdf.", e)
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
