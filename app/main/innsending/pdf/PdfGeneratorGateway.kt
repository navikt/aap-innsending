package innsending.pdf

import innsending.db.InnsendingNy
import innsending.prometheus
import io.ktor.http.*
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.NoTokenTokenProvider
import java.net.URI

class PdfGeneratorGateway(private val pdfGeneratorHost: String) {
    private val httpClient = RestClient.withDefaultResponseHandler(
        ClientConfig(),
        NoTokenTokenProvider(),
        prometheus = prometheus.prometheus
    )

    fun søknadTilPdf(innsending: InnsendingNy, navn: SøkerPdfGen.Navn): ByteArray {
        val kvittering =
            innsending.kvitteringToMap() + mapOf("mottattdato" to innsending.opprettet.toString())
        val data = SøknadPdfGen(SøkerPdfGen(navn = navn), kvittering)
        val httpPostRequest = PostRequest(
            body = data,
            additionalHeaders = listOf(Header("accept", "application/pdf"))
        )

        return requireNotNull(
            httpClient.post(
                uri = URI.create(pdfGeneratorHost + "/api/v1/genpdf/innbygger/soknad"),
                request = httpPostRequest,
                mapper = { body, _ -> body.readAllBytes() }
            )
        ) { "SøknadTilPdf - Ingen respons fra pdfgenerator" }
    }

    fun bildeTilPfd(bildeFil: ByteArray, contentType: ContentType): ByteArray {
        val postRequest = PostRequest(
            body = bildeFil,
            additionalHeaders = listOf(
                Header("Accept", ContentType.Application.Pdf.toString()),
                Header("Content-Type", contentType.toString())
            )
        )
        return requireNotNull(
            httpClient.post(
                uri = URI.create(pdfGeneratorHost + "/api/v1/genpdf/image/innbygger"),
                request = postRequest,
                mapper = { body, _ -> body.readAllBytes() }
            )
        ) { "BildeTilPdf - Ingen respons fra pdfgenerator" }
    }
}
