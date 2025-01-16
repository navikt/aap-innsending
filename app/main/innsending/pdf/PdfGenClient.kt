package innsending.pdf

import innsending.ProdConfig
import innsending.arkiv.Journalpost
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
        val navn = hentNavn(innsending.personident)

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

    fun ettersendelseTilPdf(innsending: InnsendingNy): ByteArray {
        val navn = SøkerPdfGen(hentNavn(innsending.personident))
        val data = SøknadPdfGen(navn, innsending.kvitteringToMap())

        val httpPostRequest = PostRequest(
            body = data,
            additionalHeaders = listOf(Header("accept", "application/pdf"))
        )

        return requireNotNull(
            httpClient.post(
                uri = URI.create(ProdConfig.config.pdfGenHost + "/api/v1/genpdf/aap-pdfgen/ettersending"),
                request = httpPostRequest,
                mapper = { body, _ -> body.readAllBytes() }
            )) { "Response from pdfgen was null" }
    }

    private fun hentNavn(personident: String) = oppslagClientNy.hentNavn(personident).let { (fornavn, etternavn) ->
        SøkerPdfGen.Navn(
            fornavn = fornavn,
            etternavn = etternavn,
        )
    }
}
