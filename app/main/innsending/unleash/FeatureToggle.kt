package innsending.unleash

interface FeatureToggle {
    fun key(): String
}

enum class InnsendingFeature : FeatureToggle {
    // Feature toggles opprettes i Unleash: https://aap-unleash-web.iap.nav.cloud.nais.io/projects/default
    InnsendingNySoknadPdf,
    InnsendingNyBildekonvertering,
    ;

    override fun key(): String = name
}
