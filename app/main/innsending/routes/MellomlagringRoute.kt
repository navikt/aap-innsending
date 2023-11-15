package innsending.routes

import innsending.antivirus.ClamAVClient
import innsending.antivirus.ScanResult
import innsending.pdf.PdfGen
import innsending.redis.RedisRepo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.pdfbox.Loader
import java.util.*

fun Route.mellomlagerRoute(redis: RedisRepo, virusScanClient: ClamAVClient, pdfGen: PdfGen) {
    route("/mellomlagring/vedlegg/{vedleggId}") {

        post {
            val vedleggId = requireNotNull(UUID.fromString(call.parameters["vedleggId"]))
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

            redis.mellomlagre(
                key = vedleggId,
                value = pdf
            )

            call.respond(HttpStatusCode.Created, "Vedlegg ble mellomlagret")
        }

        get {
            val vedleggId = requireNotNull(UUID.fromString(call.parameters["vedleggId"]))

            when (val vedlegg = redis.hentMellomlagring(vedleggId)) {
                null -> call.respond(HttpStatusCode.NotFound, "Fant ikke mellomlagret vedlegg")
                else -> call.respond(HttpStatusCode.OK, vedlegg)
            }
        }

        delete {
            val vedleggId = requireNotNull(UUID.fromString(call.parameters["vedleggId"]))

            redis.slettMellomlagring(vedleggId)
            call.respond(HttpStatusCode.OK)

        }
    }
}

fun sjekkPdf(fil: ByteArray): Boolean {
    val pdf = Loader.loadPDF(fil)
    return !pdf.isEncrypted
}