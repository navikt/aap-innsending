package innsending.pdf

import innsending.db.InnsendingNy
import innsending.http.HttpClientFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class PdfGeneratorGateway(private val pdfGeneratorHost: String) {
    private val httpClient = HttpClientFactory.create()

    suspend fun søknadTilPdf(innsending: InnsendingNy, navn: SøkerPdfGen.Navn): ByteArray {
        val kvittering =
            innsending.kvitteringToMap() + mapOf("mottattdato" to innsending.opprettet.toString())
        val data = SøknadPdfGen(SøkerPdfGen(navn = navn), kvittering)

        return requireNotNull(
  httpClient.post("$pdfGeneratorHost/api/v1/genpdf/innbygger/soknad") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Pdf)
            setBody(data)
        }.readRawBytes()
        ) { "SøknadTilPdf - Ingen respons fra pdfgenerator" }
    }

    suspend fun bildeTilPdf(bildeFil: ByteArray, contentType: ContentType): ByteArray {
        val res = httpClient.post("$pdfGeneratorHost/api/v1/genpdf/image/innbygger") {
            contentType(contentType)
            accept(ContentType.Application.Pdf)
            setBody(bildeFil)
        }
        return res.readRawBytes()
    }
}
