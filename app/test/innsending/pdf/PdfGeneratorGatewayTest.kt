package innsending.pdf

import innsending.Fakes
import innsending.Resource
import innsending.db.InnsendingNy
import innsending.port
import innsending.postgres.InnsendingType
import io.ktor.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PdfGeneratorGatewayTest {

    @Test
    fun `søknadTilPdf returnerer pdf-bytes`() {
        Fakes().use { fakes ->
            val gateway = PdfGeneratorGateway("http://localhost:${fakes.pdfGen.port()}")
            val navn = SøkerPdfGen.Navn(fornavn = "Ola", mellomnavn = null, etternavn = "Nordmann")

            val result = gateway.søknadTilPdf(enInnsending(), navn)

            assertThat(result).isNotEmpty()
        }
    }

    @Test
    fun `bildeTilPfd returnerer pdf-bytes for JPEG`() {
        Fakes().use { fakes ->
            val gateway = PdfGeneratorGateway("http://localhost:${fakes.pdfGen.port()}")
            val jpeg = Resource.read("/resources/images/bilde.jpg")

            val result = gateway.bildeTilPfd(jpeg, ContentType.Image.JPEG)

            assertThat(result).isNotEmpty()
        }
    }

    @Test
    fun `bildeTilPfd returnerer pdf-bytes for PNG`() {
        Fakes().use { fakes ->
            val gateway = PdfGeneratorGateway("http://localhost:${fakes.pdfGen.port()}")
            val png = Resource.read("/resources/images/bilde.png")

            val result = gateway.bildeTilPfd(png, ContentType.Image.PNG)

            assertThat(result).isNotEmpty()
        }
    }

    private fun enInnsending() = InnsendingNy(
        id = null,
        opprettet = LocalDateTime.of(2024, 1, 15, 10, 0),
        personident = "12345678910",
        soknad = null,
        data = """{"spørsmål": "svar"}""".toByteArray(),
        eksternRef = UUID.randomUUID(),
        forrigeInnsendingId = null,
        type = InnsendingType.SOKNAD,
        journalpost_Id = null,
        filer = emptyList()
    )
}
