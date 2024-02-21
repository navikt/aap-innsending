package innsending.arkiv

import innsending.Config
import innsending.http.ApiResult
import innsending.http.HttpClientWrapper
import innsending.http.Path
import io.ktor.client.request.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.ktor.client.AzureAdTokenProvider

class JoarkClient(
    config: Config,
    registry: PrometheusMeterRegistry,
) : HttpClientWrapper(config.joark, registry) {
    private val tokenProvider = AzureAdTokenProvider(config.azure, config.joark.scope)

    suspend fun opprettJournalpost(
        journalpost: Journalpost,
        callId: String
    ): ArkivResponse {
        val result = http.post(
            Path.from("/rest/journalpostapi/v1/journalpost"),
            journalpost,
        ) {
            header("Nav-Callid", callId)
        }

        return when (result) {
            is ApiResult.Ok -> result.getOrNull<ArkivResponse>() ?: error("Failed to read arkiv response from Joark")
            is ApiResult.ClientError -> error(result.getMessage())
            is ApiResult.ServerError -> error(result.getMessage())
            is ApiResult.UnknownError -> throw result.err
        }
    }

    override suspend fun getToken(): String {
        return tokenProvider.getClientCredentialToken()
    }
}
