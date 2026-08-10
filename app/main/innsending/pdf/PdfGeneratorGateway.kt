package innsending.pdf

import innsending.db.InnsendingNy
import innsending.http.HttpClientFactory
import innsending.logger
import innsending.prometheus
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.*
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.NoTokenTokenProvider
import java.net.URI

class PdfGeneratorGateway(private val pdfGeneratorHost: String) {
    private val restClient = RestClient.withDefaultResponseHandler(
        ClientConfig(),
        NoTokenTokenProvider(),
        prometheus = prometheus.prometheus
    )
    private val httpClient = HttpClientFactory.create()

    fun søknadTilPdf(innsending: InnsendingNy, navn: SøkerPdfGen.Navn): ByteArray {
        val kvittering =
            innsending.kvitteringToMap() + mapOf("mottattdato" to innsending.opprettet.toString())
        val data = SøknadPdfGen(SøkerPdfGen(navn = navn), kvittering)
        logger.info(DefaultJsonMapper.toJson(data))
        val httpPostRequest = PostRequest(
            body = data,
            additionalHeaders = listOf(Header("accept", "application/pdf"))
        )

        return requireNotNull(
            restClient.post(
                uri = URI.create(pdfGeneratorHost + "/api/v1/genpdf/innbygger/soknad"),
                request = httpPostRequest,
                mapper = { body, _ -> body.readAllBytes() }
            )
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
