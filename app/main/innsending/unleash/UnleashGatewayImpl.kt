package innsending.unleash

import innsending.ProdConfig
import io.getunleash.DefaultUnleash
import io.getunleash.util.UnleashConfig as UnleashClientConfig

object UnleashGatewayImpl : UnleashGateway {
    // Lazy: DefaultUnleash starter bakgrunnstråder for polling og metrikker, og skal kun opprettes
    // én gang. Config leses inne i lambdaen slik at manglende miljøvariabler ikke sprenger ved
    // klasseinitialisering.
    private val unleash by lazy {
        val config = ProdConfig.config.unleash
        DefaultUnleash(
            UnleashClientConfig
                .builder()
                .appName("innsending")
                .instanceId(System.getenv("HOSTNAME") ?: "innsending")
                .environment(config.environment)
                .unleashAPI("${config.apiUrl}/api")
                .apiKey(config.apiToken)
                .build()
        )
    }

    override fun isEnabled(featureToggle: FeatureToggle): Boolean {
        return unleash.isEnabled(featureToggle.key())
    }
}
