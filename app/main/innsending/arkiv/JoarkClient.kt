package innsending.arkiv

import innsending.JoarkConfig
import innsending.http.HttpClientFactory
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.aap.ktor.client.auth.azure.AzureAdTokenProvider
import no.nav.aap.ktor.client.auth.azure.AzureConfig

class JoarkClient(azureConfig: AzureConfig, private val joarkConfig: JoarkConfig) {
    private val httpClient = HttpClientFactory.create()
    private val tokenProvider = AzureAdTokenProvider(azureConfig)

    fun opprettJournalpost(
        journalpost: Journalpost,
        callId: String
    ): ArkivResponse = runBlocking {
        val token = tokenProvider.getClientCredentialToken(joarkConfig.scope)
        val response = httpClient.post("${joarkConfig.baseUrl}/rest/journalpostapi/v1/journalpost") {
            accept(ContentType.Application.Json)
            header("Nav-Callid", callId)
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(journalpost)
        }
        if (response.status.isSuccess() || response.status.value == 409) {
            response.body()
        } else {
            error("Feil mot joark (${response.status}): ${response.bodyAsText()}")
        }
    }
}
