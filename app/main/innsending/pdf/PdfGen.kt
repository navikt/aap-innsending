package innsending.pdf

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import innsending.http.HttpClientFactory
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.prometheus.client.Summary
import java.time.LocalDateTime

private const val PDFGEN_CLIENT_SECONDS_METRICNAME = "PDFGEN_client_seconds"
private val clientLatencyStats: Summary = Summary.build()
    .name(PDFGEN_CLIENT_SECONDS_METRICNAME)
    .quantile(0.5, 0.05) // Add 50th percentile (= median) with 5% tolerated error
    .quantile(0.9, 0.01) // Add 90th percentile with 1% tolerated error
    .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
    .help("Latency pdfgen, in seconds")
    .register()

class PdfGen(private val host: String) {
    private val httpClient = HttpClientFactory.create()

    suspend fun bildeTilPfd(bildeFil: ByteArray, contentType:ContentType): ByteArray =
        clientLatencyStats.startTimer().use {
            httpClient.post("$host/api/v1/genpdf/image/aap-pdfgen") {
                contentType(contentType)
                accept(ContentType.Application.Pdf)
                setBody(bildeFil)
            }
        }.body()

    suspend fun søknadTilPdf(json: ByteArray, mottattDato:LocalDateTime): ByteArray {
        val kvittering = json.toMap()
        kvittering["mottatDato"]=mottattDato.toString()
        val data=SøknadPdfGen(SøkerPdfGen(SøkerPdfGen.Navn("","")),kvittering)

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
    val kvittering: Map<String,Any>
)

data class SøkerPdfGen(
    val navn:Navn,
){
    data class Navn(
        val fornavn:String,
        val etternavn:String
    )
}

private fun ByteArray.toMap():MutableMap<String,Any>{
    val mapper = ObjectMapper()
    val tr= object:TypeReference<MutableMap<String,Any>>() {}
    return mapper.readValue(this,tr)
}

fun Map<String,Any>.toByteArray():ByteArray{
    val mapper = ObjectMapper()
    return mapper.writeValueAsBytes(this)
}