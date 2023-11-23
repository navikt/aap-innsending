package innsending.routes

import innsending.antivirus.ClamAVClient
import innsending.antivirus.ScanResult
import innsending.pdf.PdfGen
import innsending.redis.JedisRedis
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.pdfbox.Loader
import java.util.*

const val EnDag: Long = 60 * 60 * 24

private fun ApplicationCall.personident(): String {
    return requireNotNull(principal<JWTPrincipal>()) {
        "principal mangler i ktor auth"
    }.getClaim("pid", String::class)
        ?: error("pid mangler i tokenx claims")
}

fun Route.mellomlagerRoute(redis: JedisRedis, virusScanClient: ClamAVClient, pdfGen: PdfGen) {
    route("/mellomlagring/søknad") {

        post {
            //val personIdent = call.personident()
            val personIdent = requireNotNull(call.request.headers["NAV-PersonIdent"])
            redis[personIdent] = call.receive()
            redis.expire(personIdent, 3 * EnDag)
            call.respond(HttpStatusCode.OK)
        }

        get {
            //val personIdent = call.personident()
            val personIdent = requireNotNull(call.request.headers["NAV-PersonIdent"])
            when (val soknad = redis[personIdent]) {
                null -> call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret søknad")
                else -> call.respond(HttpStatusCode.OK, soknad)
            }
        }

        delete {
            //val personIdent = call.personident()
            val personIdent = requireNotNull(call.request.headers["NAV-PersonIdent"])
            redis.del(personIdent)
            call.respond(HttpStatusCode.OK)
        }
    }

    route("/mellomlagring/vedlegg") {
        post {
            val vedleggId = UUID.randomUUID().toString()
            val fil = call.receive<ByteArray>()
            val contentType = call.request.header(HttpHeaders.ContentType)

            if (virusScanClient.scan(fil).result == ScanResult.Result.FOUND) {
                call.respond(HttpStatusCode.NotAcceptable, "Fant virus i fil")
            }

            val pdf: ByteArray? = when (contentType) {
                "application/pdf" -> fil
                "image/jpeg" -> pdfGen.bildeTilPfd(fil)
                "image/png" -> pdfGen.bildeTilPfd(fil)
                else -> null
            }

            if (pdf == null) {
                call.respond(HttpStatusCode.NotAcceptable, "Filtype ikke støttet")
            }

            if (!sjekkPdf(pdf!!)) {
                call.respond(HttpStatusCode.NotAcceptable, "PDF er kryptert")
            }

            redis[vedleggId] = pdf
            redis.expire(vedleggId, 3 * EnDag)

            call.respond(HttpStatusCode.Created, vedleggId)
        }

        get("/{vedleggId}") {
            val vedleggId = requireNotNull(call.parameters["vedleggId"])

            when (val vedlegg = redis.get(vedleggId)) {
                null -> call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret vedlegg")
                else -> call.respond(HttpStatusCode.OK, vedlegg)
            }
        }

        delete("/{vedleggId}") {
            val vedleggId = requireNotNull(call.parameters["vedleggId"])

            redis.del(vedleggId)
            call.respond(HttpStatusCode.OK)
        }
    }
}

fun sjekkPdf(fil: ByteArray): Boolean {
    val pdf = Loader.loadPDF(fil)
    return !pdf.isEncrypted
}