package innsending

import no.nav.aap.kafka.streams.v2.config.StreamsConfig
import no.nav.aap.ktor.client.AzureConfig

data class Config(
    val kafka: StreamsConfig,
    val database: DbConfig,
    val azure: AzureConfig,
    val fillager: FillagerConfig
)

data class DbConfig(
    val url: String,
    val username: String,
    val password: String
)

data class FillagerConfig(
    val baseUrl: String
)
