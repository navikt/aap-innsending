package innsending.unleash

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class FakeUnleashGatewayTest {
    @Test
    fun `feature toggle key er lik enum-navnet`() {
        assertThat(InnsendingFeature.PlaceholderToggle.key()).isEqualTo("PlaceholderToggle")
    }
}
