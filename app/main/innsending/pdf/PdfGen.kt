package innsending.pdf

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import innsending.Config
import innsending.http.HttpClientWrapper
import innsending.http.HttpResult
import innsending.http.Path
import innsending.oppslag.OppslagClient
import innsending.postgres.InnsendingMedFiler
import io.ktor.http.*
import io.micrometer.core.instrument.MeterRegistry

typealias Image = ByteArray
typealias Json = ByteArray
typealias Pdf = ByteArray

class PdfGen(
    config: Config,
    registry: MeterRegistry,
) : HttpClientWrapper(
    config.pdfGen,
    registry,
) {
    private val pdlClient = OppslagClient(config)

    override suspend fun getToken(): String = "no auth"

    suspend fun bildeTilPfd(img: Image, contentType: ContentType): HttpResult<Pdf>? {
        return http.post<Image, Pdf>(
            path = Path.from("/api/v1/genpdf/image/aap-pdfgen"),
            body = img
        ) {
            contentType(contentType)
        }
    }

    suspend fun søknadTilPdf(innsending: InnsendingMedFiler): HttpResult<Pdf>? {
        val json = innsending.data ?: error("mangler søknaden fra innsending (innsending.data)")
        val personident = innsending.personident

        val navn = pdlClient.hentNavn(personident).let { navn ->
            SøkerPdfGen.Navn(
                fornavn = navn.fornavn,
                etternavn = navn.etternavn,
            )
        }

        val kvittering = json.toMap() + mapOf("mottattDato" to innsending.opprettet.toString())
        val søker = SøkerPdfGen(navn)

        return http.post<SøknadPdfGen, Pdf>(
            path = Path.from("/api/v1/genpdf/aap-pdfgen/soknad"),
            body = SøknadPdfGen(søker, kvittering),
        )
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
