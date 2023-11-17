package innsending.pdf

import innsending.http.HttpClientFactory
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.*
import io.prometheus.client.Summary

private const val PDFGEN_CLIENT_SECONDS_METRICNAME = "PDFGEN_client_seconds"
private val clientLatencyStats: Summary = Summary.build()
    .name(PDFGEN_CLIENT_SECONDS_METRICNAME)
    .quantile(0.5, 0.05) // Add 50th percentile (= median) with 5% tolerated error
    .quantile(0.9, 0.01) // Add 90th percentile with 1% tolerated error
    .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
    .help("Latency pdfgen, in seconds")
    .register()

class PdfGen {
    private val httpClient = HttpClientFactory.create()

    suspend fun bildeTilPfd(bildeFil: ByteArray): ByteArray =
        clientLatencyStats.startTimer().use {
            httpClient.post("http://pdfgen/api/v1/genpdf/image/fillager") {
                accept(ContentType.Application.Json)
                setBody(bildeFil)
                contentType(ContentType.Application.Json)
            }
        }.body()

    // TODO: finn riktig URI til pdfgen
    suspend fun søknadTilPdf(json: ByteArray) =
        clientLatencyStats.startTimer().use {
            httpClient.post("http://pdfgen/api/v1/genpdf/") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Pdf)
                setBody(json)
            }
        }
}
