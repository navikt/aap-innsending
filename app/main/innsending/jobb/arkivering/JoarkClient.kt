package innsending.jobb.arkivering

import innsending.ProdConfig
import innsending.arkiv.ArkivResponse
import innsending.arkiv.Journalpost
import innsending.prometheus
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.komponenter.json.DefaultJsonMapper
import java.net.URI
import java.time.Duration

class JoarkClient {
    private val joarkConfig = ProdConfig.config.joark
    private val httpClient = RestClient.withDefaultResponseHandler(
        config = ClientConfig(joarkConfig.scope),
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus.prometheus
    )

    fun opprettJournalpost(
        journalpost: Journalpost
    ): ArkivResponse {
        val httpPostRequest = PostRequest(
            body = journalpost,
            timeout = Duration.ofMinutes(5)
        )
        val response: ArkivResponse? = httpClient.post(
            uri = URI.create(joarkConfig.baseUrl + "/rest/journalpostapi/v1/journalpost"),
            request = httpPostRequest,
            mapper = { body, _ ->
                DefaultJsonMapper.fromJson(body)
            })
        return requireNotNull(response) { "Response from joark was null" }
    }

}


