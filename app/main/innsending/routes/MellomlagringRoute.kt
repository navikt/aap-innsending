package innsending.routes

import innsending.antivirus.ClamAVClient
import innsending.pdf.PdfGen
import innsending.redis.Redis
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.pdfbox.Loader
import java.util.*

const val EnDag: Long = 60 * 60 * 24

internal fun ApplicationCall.personident(): String {
    return requireNotNull(principal<JWTPrincipal>()) {
        "principal mangler i ktor auth"
    }.getClaim("pid", String::class)
        ?: error("pid mangler i tokenx claims")
}

fun Route.mellomlagerRoute(redis: Redis, virusScanClient: ClamAVClient, pdfGen: PdfGen) {
    route("/mellomlagring/søknad") {

        post {
            val personIdent = call.personident()
            redis[personIdent] = call.receive()
            redis.expire(personIdent, 3 * EnDag)
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
                    val acceptedContentType =
                        listOf(ContentType.Image.JPEG, ContentType.Image.PNG, ContentType.Application.Pdf)

                    val pdf: ByteArray = when (contentType) {
                        in acceptedContentType -> {
                            if (virusScanClient.hasVirus(fil, contentType)) {
                                return@post call.respond(HttpStatusCode.NotAcceptable, "Fant virus i fil")
                            }

                            pdfGen.bildeTilPfd(fil, contentType)
                        }

                        else -> {
                            return@post call.respond(HttpStatusCode.NotAcceptable, "Filtype ikke støttet")
                        }
                    }

                    if (!sjekkPdf(pdf)) {
                        return@post call.respond(HttpStatusCode.NotAcceptable, "PDF er kryptert")
                    }

                    redis[filId] = pdf
                    redis.expire(filId, 3 * EnDag)

                    call.respond(HttpStatusCode.Created, filId)
                }

                else -> {
                    return@post call.respond(HttpStatusCode.NotAcceptable, "Filtype ikke støttet")
                }

            }
        }

        get("/{filId}") {
            val filId = requireNotNull(call.parameters["filId"])

            when (val fil = redis[filId]) {
                null -> call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret fil")
                else -> call.respond(HttpStatusCode.OK, fil)
            }
        }

        delete("/{filId}") {
            val filId = requireNotNull(call.parameters["filId"])

            redis.del(filId)
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun sjekkPdf(fil: ByteArray): Boolean {
    val pdf = Loader.loadPDF(fil)
    return !pdf.isEncrypted
}