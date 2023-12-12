package innsending.routes

import innsending.antivirus.ClamAVClient
import innsending.auth.personident
import innsending.pdf.PdfGen
import innsending.redis.EnDagSekunder
import innsending.redis.Redis
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.pdfbox.Loader
import java.util.*


private val acceptedContentType =
    listOf(ContentType.Image.JPEG, ContentType.Image.PNG, ContentType.Application.Pdf)

fun Route.mellomlagerRoute(redis: Redis, virusScanClient: ClamAVClient, pdfGen: PdfGen) {
    route("/mellomlagring/søknad") {

        post {
            val personIdent = call.personident()
            redis.set(personIdent, call.receive(), EnDagSekunder)
            call.respond(HttpStatusCode.OK)
        }

        get {
            val personIdent = call.personident()
            when (val soknad = redis[personIdent]) {
                null -> call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret søknad")
                else -> call.respond(HttpStatusCode.OK, soknad)
            }
        }

        delete {
            val personIdent = call.personident()
            redis.del(personIdent)
            call.respond(HttpStatusCode.OK)
        }
    }

    route("/mellomlagring/fil") {
        post {
            val filId = UUID.randomUUID().toString()

            when (val mottattFil = call.receiveMultipart().readAllParts().single()) {
                is PartData.FileItem -> {
                    val fil = mottattFil.streamProvider().readBytes()
                    val contentType = requireNotNull(mottattFil.contentType) { "contentType i multipartForm mangler" }

                    val pdf: ByteArray = when (contentType) {
                        in acceptedContentType -> {
                            if (virusScanClient.hasVirus(fil, contentType)) {
                                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRespons("Fant virus i fil"))
                            }

                            pdfGen.bildeTilPfd(fil, contentType)
                        }

                        else -> {
                            return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRespons("Filtype ikke støttet"))
                        }
                    }

                    if (!sjekkPdf(pdf)) {
                        return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRespons("PDF er kryptert"))
                    }

                    redis.set(filId, pdf, EnDagSekunder)

                    call.respond(status = HttpStatusCode.Created, MellomlagringRespons(filId))
                }

                else -> {
                    return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRespons("Filtype ikke støttet"))
                }
            }
        }

        get("/{filId}") {
            val filId = requireNotNull(call.parameters["filId"])

            when (val fil = redis[filId]) {
                null -> call.respond(HttpStatusCode.NotFound, ErrorRespons("Fant ikke mellomlagret fil"))
                else -> {
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment
                            .withParameter(ContentDisposition.Parameters.FileName, "${filId}.pdf")
                            .toString()
                    )
                    call.respond(HttpStatusCode.OK, fil)
                }
            }
        }

        delete("/{filId}") {
            val filId = requireNotNull(call.parameters["filId"])

            redis.del(filId)
            call.respond(HttpStatusCode.OK)
        }
    }
}

data class MellomlagringRespons(
    val filId: String,
)

data class ErrorRespons(
    val feilmelding: String,
)

fun sjekkPdf(fil: ByteArray): Boolean {
    val pdf = Loader.loadPDF(fil)
    return !pdf.isEncrypted
}