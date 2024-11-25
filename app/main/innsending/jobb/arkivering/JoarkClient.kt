package innsending.jobb.arkivering

import innsending.ProdConfig
import innsending.arkiv.ArkivResponse
import innsending.arkiv.Journalpost
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import java.net.URI

class JoarkClient {
    private val joarkConfig = ProdConfig.config.joark
    private val httpClient = RestClient.withDefaultResponseHandler(
        config = ClientConfig(joarkConfig.scope),
        tokenProvider = ClientCredentialsTokenProvider
    )

    fun opprettJournalpost(
        journalpost: Journalpost
    ): ArkivResponse {
        val httpPostRequest = PostRequest(
            body = journalpost,
        )
        val response: ArkivResponse? = httpClient.post(
            uri = URI.create(joarkConfig.baseUrl+"/rest/journalpostapi/v1/journalpost"),
            request = httpPostRequest,
            mapper = { body, _ ->
                DefaultJsonMapper.fromJson(body)
            })
        return requireNotNull(response) { "Response from joark was null" }
    }

}


