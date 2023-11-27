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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val secureLog: Logger = LoggerFactory.getLogger("securelog")
private const val JOARK_CLIENT_SECONDS_METRICNAME = "joark_client_seconds"
private val clientLatencyStats: Summary = Summary.build()
    .name(JOARK_CLIENT_SECONDS_METRICNAME)
    .quantile(0.5, 0.05) // Add 50th percentile (= median) with 5% tolerated error
    .quantile(0.9, 0.01) // Add 90th percentile with 1% tolerated error
    .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
    .help("Latency joark, in seconds")
    .register()

class JoarkClient(azureConfig: AzureConfig, private val joarkConfig: JoarkConfig) {
    private val tokenProvider = AzureAdTokenProvider(azureConfig, "fillagerScope")
    private val httpClient = HttpClientFactory.create()

    fun opprettJournalpost(
        journalpost: Journalpost,
        callId:String
    ): Boolean =
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
                when(response.status.value) {
                    409 -> true // Dette betyr at vi allerede har opprettet denne, men lett hos oss feilet
                    in 200..299 -> true
                    else -> false.also {
                        secureLog.error("Feil fra joark: {}, {}", response.status.value, response)
                    }
                }
            }
        }
}
