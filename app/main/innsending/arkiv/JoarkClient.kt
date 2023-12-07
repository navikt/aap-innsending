package innsending.arkiv

import innsending.JoarkConfig
import innsending.http.HttpClientFactory
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.aap.ktor.client.AzureAdTokenProvider
import no.nav.aap.ktor.client.AzureConfig

private const val JOARK_CLIENT_SECONDS_METRICNAME = "joark_client_seconds"
private val clientLatencyStats: Summary = Summary.build()
    .name(JOARK_CLIENT_SECONDS_METRICNAME)
    .quantile(0.5, 0.05) // Add 50th percentile (= median) with 5% tolerated error
    .quantile(0.9, 0.01) // Add 90th percentile with 1% tolerated error
    .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
    .help("Latency joark, in seconds")
    .register()

class JoarkClient(azureConfig: AzureConfig, private val joarkConfig: JoarkConfig) {
    private val tokenProvider = AzureAdTokenProvider(azureConfig, joarkConfig.scope)
    private val httpClient = HttpClientFactory.create()

    fun opprettJournalpost(
        journalpost: Journalpost,
        callId: String
    ): ArkivResponse? =
        clientLatencyStats.startTimer().use {
            runBlocking {
                val token = tokenProvider.getClientCredentialToken()
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
                    null
                }
            }
        }
}
