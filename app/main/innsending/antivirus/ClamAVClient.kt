package innsending.antivirus

import com.fasterxml.jackson.annotation.JsonProperty
import innsending.http.HttpClientFactory
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.prometheus.client.Summary

private const val CLAMAV_CLIENT_SECONDS_METRICNAME = "CLAMAV_client_seconds"
private val clientLatencyStats: Summary = Summary.build()
    .name(CLAMAV_CLIENT_SECONDS_METRICNAME)
    .quantile(0.5, 0.05) // Add 50th percentile (= median) with 5% tolerated error
    .quantile(0.9, 0.01) // Add 90th percentile with 1% tolerated error
    .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
    .help("Latency clamav, in seconds")
    .register()

class ClamAVClient(private val host:String) {
    private val httpClient = HttpClientFactory.create()
    suspend fun hasVirus(fil: ByteArray, contentType: ContentType): Boolean =
        clientLatencyStats.startTimer().use {
            httpClient.put("$host/scan") {
                accept(ContentType.Application.Json)
                setBody(fil)
                contentType(contentType)
            }
        }
            .body<List<ScanResult>>()
            .any{ it.result == ScanResult.Result.FOUND }
}


data class ScanResult(@JsonProperty("Result") val result: Result) {
    enum class Result {
        FOUND,
        OK,
        NONE
    }

    companion object {
        val FEIL = listOf(ScanResult(Result.NONE))
    }
}