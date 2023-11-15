package innsending

import no.nav.aap.ktor.client.AzureConfig

data class Config(
    val postgres: PostgresConfig,
    val redis: RedisConfig,
    val azure: AzureConfig,
)

data class RedisConfig(
    val host: String,
    val port: Int,
    val pwd: String,
)

data class PostgresConfig(
    val url: String,
    val username: String,
    val password: String
)
