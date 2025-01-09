package innsending.pdf

import innsending.ProdConfig
import innsending.db.InnsendingNy
import innsending.oppslag.OppslagClientNy
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.NoTokenTokenProvider
import java.net.URI

class PdfGenClient {
    private val httpClient = RestClient.withDefaultResponseHandler(ClientConfig(), NoTokenTokenProvider())
    private val oppslagClientNy = OppslagClientNy()

    fun søknadTilPdf(innsending: InnsendingNy): ByteArray {
        val navn = oppslagClientNy.hentNavn(innsending.personident).let { navn ->
            SøkerPdfGen.Navn(
                fornavn = navn.fornavn,
                etternavn = navn.etternavn,
            )
        }

        val kvittering = innsending.kvitteringToMap() + mapOf("mottattdato" to innsending.opprettet.toString())
        val data = SøknadPdfGen(SøkerPdfGen(navn = navn), kvittering)
        val httpPostRequest = PostRequest(
            body = data,
            additionalHeaders = listOf(Header("accept", "application/pdf"))
        )

        return requireNotNull(
            httpClient.post(
            uri = URI.create(ProdConfig.config.pdfGenHost + "/api/v1/genpdf/aap-pdfgen/soknad"),
            request = httpPostRequest,
            mapper = { body, _ -> body.readAllBytes() }
        )) { "Response from pdfgen was null" }
    }
}
