package innsending.fillager

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.prometheus.client.Summary
import org.slf4j.LoggerFactory

private const val PDFGEN_CLIENT_SECONDS_METRICNAME = "PDFGEN_client_seconds"
private val secureLog = LoggerFactory.getLogger("secureLog")
private val clientLatencyStats: Summary = Summary.build()
    .name(PDFGEN_CLIENT_SECONDS_METRICNAME)
    .quantile(0.5, 0.05) // Add 50th percentile (= median) with 5% tolerated error
    .quantile(0.9, 0.01) // Add 90th percentile with 1% tolerated error
    .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
    .help("Latency joark, in seconds")
    .register()


class PdfGen {
    suspend fun bildeTilPfd(bildeFil: ByteArray): ByteArray =
        clientLatencyStats.startTimer().use {
            httpClient.post("http://pdfgen/api/v1/genpdf/image/fillager") {
                accept(ContentType.Application.Json)
                setBody(bildeFil)
                contentType(ContentType.Application.Json)
            }
        }.body()

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout)
        install(HttpRequestRetry)
        install(Logging) {
            level = LogLevel.BODY
        }

        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(JavaTimeModule())
            }
        }
    }
}
