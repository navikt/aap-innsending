package innsending.oppslag

import innsending.Config
import innsending.http.ApiResult
import innsending.http.HttpClientWrapper
import innsending.http.Path
import io.ktor.client.request.*
import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.ktor.client.auth.azure.AzureAdTokenProvider
import java.util.*

class OppslagClient(
    config: Config,
    registry: MeterRegistry,
) : HttpClientWrapper(
    config.oppslag,
    registry,
) {
    private val scope = config.oppslag.scope
    private val tokenProvider = AzureAdTokenProvider(config.azure)

    suspend fun hentNavn(personident: String): ApiResult {
        return http.get(Path.from("/person/navn")) {
            header("personident", personident)
            header("Nav-CallId", UUID.randomUUID().toString())
        }
    }

    override suspend fun getToken(): String {
        return tokenProvider.getClientCredentialToken(scope)
    }
}

data class Navn(
    val fornavn: String,
    val etternavn: String,
)
