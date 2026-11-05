package innsending.jobb.arkivering

import innsending.db.FilNy
import innsending.db.InMemoryFilData
import innsending.db.InnsendingNy
import innsending.postgres.InnsendingType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

class ArkiveringServiceTest {
    @Test
    fun `ettersending uten tidligere innsending får brevkode på hoveddokumentet`() {
        val innsending = innsending(InnsendingType.ETTERSENDING, antallFiler = 1)

        val dokument = lagDokumenter(innsending).single()

        assertThat(dokument.brevkode).isEqualTo("NAVe 11-13.05")
        assertThat(dokument.tittel).isEqualTo(innsending.filer.single().tittel)
        assertThat(Base64.getDecoder().decode(dokument.dokumentVarianter.single().fysiskDokument))
            .isEqualTo(innsending.filer.single().data.hent())
    }

    @Test
    fun `ettersending til tidligere innsending får brevkode bare på hoveddokumentet`() {
        val innsending = innsending(InnsendingType.ETTERSENDING, antallFiler = 2)
            .copy(forrigeInnsendingId = 1L)

        val dokumenter = lagDokumenter(innsending)

        assertThat(dokumenter.map { it.brevkode }).containsExactly("NAVe 11-13.05", "")
        assertThat(dokumenter.map { it.tittel }).containsExactlyElementsOf(innsending.filer.map { it.tittel })
    }

    @Test
    fun `vedlegg til søknad beholder tom brevkode`() {
        val dokumenter = lagDokumenter(innsending(InnsendingType.SOKNAD, antallFiler = 2))

        assertThat(dokumenter.map { it.brevkode }).containsExactly("", "")
    }

    private fun innsending(type: InnsendingType, antallFiler: Int) = InnsendingNy(
        id = null,
        opprettet = LocalDateTime.of(2026, 1, 15, 10, 0),
        personident = "12345678910",
        soknad = if (type == InnsendingType.SOKNAD) "{}".toByteArray() else null,
        data = null,
        eksternRef = UUID.randomUUID(),
        forrigeInnsendingId = null,
        type = type,
        journalpost_Id = null,
        filer = (1..antallFiler).map { index ->
            FilNy(tittel = "Dokument $index", data = InMemoryFilData("Innhold $index".toByteArray()))
        }
    )
}
