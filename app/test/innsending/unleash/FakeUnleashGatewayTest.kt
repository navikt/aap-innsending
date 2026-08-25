package innsending.unleash

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class FakeUnleashGatewayTest {
    @Test
    fun `fake returnerer konfigurert verdi`() {
        assertThat(FakeUnleashGateway(enabled = true).isEnabled(InnsendingFeature.InnsendingNySoknadPdf)).isTrue()
        assertThat(FakeUnleashGateway(enabled = false).isEnabled(InnsendingFeature.InnsendingNySoknadPdf)).isFalse()
    }

    @Test
    fun `feature toggle key er lik enum-navnet`() {
        assertThat(InnsendingFeature.InnsendingNySoknadPdf.key()).isEqualTo("InnsendingNySoknadPdf")
    }
}
