package innsending.arkiv

import innsending.Config
import innsending.http.ApiResult
import innsending.http.Path
import innsending.http.RestClient
import io.ktor.client.request.*
import io.ktor.http.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.aap.ktor.client.auth.azure.AzureAdTokenProvider

class JoarkClient(
    config: Config,
    registry: PrometheusMeterRegistry,
) {
    private val client: RestClient = RestClient(config.joark, registry)
    private val scope = config.joark.scope
    private val tokenProvider = AzureAdTokenProvider(config.azure)

    suspend fun opprettJournalpost(
        journalpost: Journalpost,
        callId: String
    ): ArkivResponse {
        val result = client.post(Path.from("/rest/journalpostapi/v1/journalpost")) {
            bearerAuth(clientCredentials)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            header("Nav-Callid", callId)
            setBody(journalpost)
        }

        return when (result) {
            is ApiResult.Ok -> result.getOrNull<ArkivResponse>() ?: error("Failed to read arkiv response from Joark")
            is ApiResult.ClientError -> error(result.getMessage())
            is ApiResult.ServerError -> error(result.getMessage())
            is ApiResult.UnknownError -> throw result.err
        }
    }

    private val clientCredentials: String
        get() = runBlocking {
            tokenProvider.getClientCredentialToken(scope)
        }
}
