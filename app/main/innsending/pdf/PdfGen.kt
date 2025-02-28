package innsending.pdf

import innsending.Config
import innsending.http.HttpClientFactory
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.slf4j.LoggerFactory
import java.security.MessageDigest

private val logger = LoggerFactory.getLogger("PdfGen")

class PdfGen(config: Config) {
    private val host = config.pdfGenHost
    private val httpClient = HttpClientFactory.create()

    suspend fun bildeTilPfd(bildeFil: ByteArray, contentType: ContentType): ByteArray {
        val res = httpClient.post("$host/api/v1/genpdf/image/aap-pdfgen") {
            contentType(contentType)
            accept(ContentType.Application.Pdf)
            setBody(bildeFil)
        }

        if (res.status.value >= 300) {
            logger.error(
                "Feil i pdfgen: status ${res.status}. Bilde til PDF. Størrelse: ${bildeFil.size / 1024} kb. Hash av fil: ${
                    hashByteArray(
                        bildeFil,
                        "SHA-256"
                    )
                }"
            )
            throw Exception("Feil i pdfGen")
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
        val mellomnavn: String?,
        val etternavn: String
    )
}
