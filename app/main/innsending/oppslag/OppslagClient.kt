package innsending.oppslag

import innsending.Config
import innsending.http.ApiResult
import innsending.http.HttpClientWrapper
import innsending.http.Path
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.ktor.client.AzureAdTokenProvider
import java.util.*

class OppslagClient(
    config: Config,
    registry: MeterRegistry,
) : HttpClientWrapper(
    config.oppslag,
    registry,
) {
    private val oppslagConfig = config.oppslag
    private val tokenProvider = AzureAdTokenProvider(config.azure, oppslagConfig.scope)

    suspend fun hentNavn(personident: String): ApiResult {
        return http.get(Path.from("/person/navn")) {
            header("personident", personident)
            header("Nav-CallId", UUID.randomUUID().toString())
        }
    }

    override suspend fun getToken(): String {
        return tokenProvider.getClientCredentialToken()
    }
}

data class Navn(
    val fornavn: String,
    val etternavn: String,
)