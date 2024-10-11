package innsending.pdf

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import innsending.Config
import innsending.http.HttpClientFactory
import innsending.oppslag.OppslagClient
import innsending.postgres.InnsendingMedFiler
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest

private val logger = LoggerFactory.getLogger("PdfGen")

class PdfGen(config: Config) {
    private val host = config.pdfGenHost
    private val httpClient = HttpClientFactory.create()
    private val pdlClient = OppslagClient(config)

    suspend fun bildeTilPfd(bildeFil: ByteArray, contentType: ContentType): ByteArray {
        logger.info("Bilde til PDF. Hash av fil: ${hashByteArray(bildeFil, "SHA-256")}")
        val res = httpClient.post("$host/api/v1/genpdf/image/aap-pdfgen") {
            contentType(contentType)
            accept(ContentType.Application.Pdf)
            setBody(bildeFil)
        }

        if (res.status.value >= 300) {
            logger.error("feil i pdfgen: status ${res.status}")
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

        val res = httpClient.post("$host/api/v1/genpdf/aap-pdfgen/soknad") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Pdf)
            setBody(data)
        }

        return res.body()
    }
}

fun hashByteArray(byteArray: ByteArray, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    val hashBytes = digest.digest(byteArray)

    // Convert a byte array to hexadecimal string
    return hashBytes.joinToString("") { "%02x".format(it) }
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
