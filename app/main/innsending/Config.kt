package innsending

import no.nav.aap.ktor.client.AzureConfig

data class Config(
    val database: DbConfig,
    val redis: RedisConfig,
    val azure: AzureConfig,
    val fillager: FillagerConfig
)

data class RedisConfig(
    val host: String,
    val port: Int,
    val pwd: String,
)

data class DbConfig(
    val url: String,
    val username: String,
    val password: String
)

data class FillagerConfig(
    val baseUrl: String
)
