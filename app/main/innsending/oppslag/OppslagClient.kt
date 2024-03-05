package innsending.oppslag

import innsending.Config
import innsending.http.ApiResult
import innsending.http.Path
import innsending.http.RestClient
import io.ktor.client.request.*
import io.ktor.http.*
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.aap.ktor.client.auth.azure.AzureAdTokenProvider
import java.util.*

class OppslagClient(
    config: Config,
    registry: MeterRegistry,
) {
    private val client: RestClient = RestClient(config.clamAV, registry)
    private val scope = config.oppslag.scope
    private val tokenProvider = AzureAdTokenProvider(config.azure)

    suspend fun hentNavn(personident: String): ApiResult {
        return client.get(Path.from("/person/navn")) {
            bearerAuth(clientCredentials)
            accept(ContentType.Application.Json)
            header("Nav-CallId", UUID.randomUUID().toString())
            header("personident", personident)
        }
    }

    private val clientCredentials: String
        get() = runBlocking {
            tokenProvider.getClientCredentialToken(scope)
        }
}

data class Navn(
    val fornavn: String,
    val etternavn: String,
)
