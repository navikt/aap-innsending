package innsending.jobb.arkivering

import innsending.db.FilNy
import innsending.db.InnsendingNy
import innsending.pdf.PdfGenClient
import innsending.postgres.InnsendingType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class ArkiveringServiceTest {

    val pdfGen: PdfGenClient = mockk(relaxed = true)
    val joarkClient: JoarkClient = mockk(relaxed = true)

    val arkiveringService = ArkiveringService(
        joarkClient,
        pdfGen
    )

    @Test
    fun `arkiverEttersendelseInnsending håndterer innsendinger uten kvittering`() {

        val innsending = genererInnsending(null)

        every { pdfGen.ettersendelseTilPdf(any() as InnsendingNy) } answers { requireNotNull((firstArg() as InnsendingNy).data) }

        arkiveringService.arkiverEttersendelseInnsending(innsending)

        verify { joarkClient.opprettJournalpost(withArg { assertThat(it.dokumenter).hasSize(2) }) }

    }

    @Test
    fun `arkiverEttersendelseInnsending håndterer innsendinger med kvittering`() {

        val innsending = genererInnsending(ByteArray(0))

        val kviteringPdf = ByteArray(111)

        every { pdfGen.ettersendelseTilPdf(any() as InnsendingNy) } returns  kviteringPdf

        arkiveringService.arkiverEttersendelseInnsending(innsending)

        verify { joarkClient.opprettJournalpost(withArg { assertThat(it.dokumenter.first().brevkode).isEqualTo("NAVe 11-13.05") }) }

    }

    private fun genererInnsending(data: ByteArray?) = InnsendingNy(
        id = 1,
        opprettet = LocalDateTime.now(),
        personident = "personident",
        soknad = null,
        data = data,
        eksternRef = UUID.randomUUID(),
        forrigeInnsendingId = null,
        type = InnsendingType.ETTERSENDING,
        journalpost_Id = null,
        filer = listOf(
            FilNy("Fil1", ByteArray(0)),
            FilNy("Fil2", ByteArray(0))
        )
    )
}