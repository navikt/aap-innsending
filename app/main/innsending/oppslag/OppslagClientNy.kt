package innsending.oppslag

import innsending.ProdConfig
import java.net.URI
import java.util.UUID
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureM2MTokenProvider
import no.nav.aap.komponenter.json.DefaultJsonMapper

class OppslagClientNy {
    private val httpClient = RestClient.withDefaultResponseHandler(
        config = ClientConfig(ProdConfig.config.oppslag.scope),
        tokenProvider = AzureM2MTokenProvider
    )

    fun hentNavn(personident: String): Navn {
        val request = GetRequest(
            additionalHeaders = listOf(
                Header("personident", personident),
                Header("Nav-CallId", UUID.randomUUID().toString())
            )
        )
        val response: Navn? = httpClient.get(
            uri = URI.create(ProdConfig.config.oppslag.host + "/person/navn"),
            request = request,
            mapper = { body, _ -> DefaultJsonMapper.fromJson(body) }
        )
        return requireNotNull(response) { "Response from oppslag was null" }
    }
}
