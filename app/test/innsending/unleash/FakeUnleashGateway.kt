package innsending.unleash

class FakeUnleashGateway(
    private val enabled: Boolean = false,
) : UnleashGateway {
    override fun isEnabled(featureToggle: FeatureToggle): Boolean = enabled
}
