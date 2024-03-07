package innsending.pdf

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import innsending.Config
import innsending.SECURE_LOGGER
import innsending.http.HttpClientFactory
import innsending.oppslag.OppslagClient
import innsending.postgres.InnsendingMedFiler
import io.ktor.client.call.*
import io.ktor.client.request.*
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

class PdfGen(config: Config) {
    private val host = config.pdfGenHost
    private val httpClient = HttpClientFactory.create()
    private val pdlClient = OppslagClient(config)

    suspend fun bildeTilPfd(bildeFil: ByteArray, contentType: ContentType): ByteArray {
        val res = clientLatencyStats.startTimer().use {
            httpClient.post("$host/api/v1/genpdf/image/aap-pdfgen") {
                contentType(contentType)
                accept(ContentType.Application.Pdf)
                setBody(bildeFil)
            }
        }
        if (res.status.value >= 300) {
            SECURE_LOGGER.error("feil i pdfgen: status ${res.status}")
            throw Exception("Feil i pdfGen")
        }
        return res.body()
    }


    suspend fun søknadTilPdf(innsending: InnsendingMedFiler): ByteArray {
        val json = innsending.data ?: error("mangler søknaden fra innsending (innsending.data)")
        val personident = innsending.personident

        val navn = pdlClient.hentNavn(personident).let { navn ->
            SøkerPdfGen.Navn(
                fornavn = navn.fornavn,
                etternavn = navn.etternavn,
            )
        }

        val kvittering = json.toMap() + mapOf("mottattdato" to innsending.opprettet.toString())
        val data = SøknadPdfGen(SøkerPdfGen(navn), kvittering)

        return clientLatencyStats.startTimer().use {
            httpClient.post("$host/api/v1/genpdf/aap-pdfgen/soknad") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Pdf)
                setBody(data)
            }
        }.body()
    }
}

data class SøknadPdfGen(
    val søker: SøkerPdfGen,
    val kvittering: Map<String, Any>
)

data class SøkerPdfGen(
    val navn: Navn,
) {
    data class Navn(
        val fornavn: String,
        val etternavn: String
    )
}

private fun ByteArray.toMap(): Map<String, Any> {
    val mapper = ObjectMapper()
    val tr = object : TypeReference<Map<String, Any>>() {}
    return mapper.readValue(this, tr)
}
