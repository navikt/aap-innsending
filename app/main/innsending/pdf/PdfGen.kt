package innsending.pdf

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import innsending.Config
import innsending.http.ApiResult
import innsending.http.Path
import innsending.http.RestClient
import innsending.oppslag.Navn
import innsending.oppslag.OppslagClient
import innsending.postgres.InnsendingMedFiler
import io.ktor.client.request.*
import io.ktor.http.*
import io.micrometer.core.instrument.MeterRegistry

typealias Image = ByteArray
typealias Pdf = ByteArray

class PdfGen(
    config: Config,
    registry: MeterRegistry
) {
    private val client = RestClient(config.pdfGen, registry)
    private val oppslagClient = OppslagClient(config, registry)

    suspend fun bildeTilPfd(img: Image, contentType: ContentType): ApiResult {
        return client.post(Path.from("/api/v1/genpdf/image/aap-pdfgen")) {
            accept(ContentType.Application.Json)
            contentType(contentType)
            setBody(img)
        }
    }

    suspend fun søknadTilPdf(innsending: InnsendingMedFiler): Pdf {
        val pdlNavn = when (val oppslagResult = oppslagClient.hentNavn(innsending.personident)) {
            is ApiResult.Ok -> oppslagResult.getOrNull<Navn>() ?: error("Failed to get navn from oppslag result")
            is ApiResult.ClientError -> error(oppslagResult.getMessage())
            is ApiResult.ServerError -> error(oppslagResult.getMessage())
            is ApiResult.UnknownError -> throw oppslagResult.err
        }

        val navn = SøkerPdfGen.Navn(pdlNavn.fornavn, pdlNavn.etternavn)
        val json = innsending.data ?: error("mangler søknaden fra innsending (innsending.data)")
        val kvittering = json.toMap() + mapOf("mottattDato" to innsending.opprettet.toString())
        val søker = SøkerPdfGen(navn)

        val response = client.post(Path.from("/api/v1/genpdf/aap-pdfgen/soknad")) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(SøknadPdfGen(søker, kvittering))
        }

        val pdf = when (response) {
            is ApiResult.Ok -> response.getOrNull<Pdf>() ?: error("Failed to get pdf from pdf-gen response")
            is ApiResult.ClientError -> error(response.getMessage())
            is ApiResult.ServerError -> error(response.getMessage())
            is ApiResult.UnknownError -> throw response.err
        }

        return pdf
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
