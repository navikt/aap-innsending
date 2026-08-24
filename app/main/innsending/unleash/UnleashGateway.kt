package innsending.unleash

interface UnleashGateway {
    fun isEnabled(featureToggle: FeatureToggle): Boolean
}
