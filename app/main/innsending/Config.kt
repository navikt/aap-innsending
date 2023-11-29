package innsending

import no.nav.aap.ktor.client.AzureConfig
import java.net.URI

data class Config(
    val postgres: PostgresConfig = PostgresConfig(),
    val redis: RedisConfig = RedisConfig(),
    val azure: AzureConfig = AzureConfig(
        tokenEndpoint = URI.create(getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")).toURL(),
        clientId = getEnvVar("AZURE_APP_CLIENT_ID"),
        clientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET")
    ),
    val joark: JoarkConfig = JoarkConfig(),
    val tokenx: TokenXConfig = TokenXConfig(),
    val pdfGenHost: String = "http://pdfgen",
    val virusScanHost: String = "http://clamav.nais-system",
    val environment: String = getEnvVar("NAIS_CLUSTER_NAME")
)

data class RedisConfig(
    val uri: URI = URI(getEnvVar("REDIS_URI_MELLOMLAGER").also { SECURE_LOGGER.info("redis uri $it") }),
    val username: String = getEnvVar("REDIS_USERNAME_MELLOMLAGER"),
    val password: String = getEnvVar("REDIS_PASSWORD_MELLOMLAGER"),
)

data class PostgresConfig(
    val host: String = getEnvVar("NAIS_DATABASE_INNSENDING_INNSENDING_HOST"),
    val port: String = getEnvVar("NAIS_DATABASE_INNSENDING_INNSENDING_PORT"),
    val database: String = getEnvVar("NAIS_DATABASE_INNSENDING_INNSENDING_DATABASE"),
    val username: String = getEnvVar("NAIS_DATABASE_INNSENDING_INNSENDING_USERNAME"),
    val password: String = getEnvVar("NAIS_DATABASE_INNSENDING_INNSENDING_PASSWORD"),
    val url: String = "jdbc:postgresql://${host}:${port}/${database}",
    val driver: String ="org.postgresql.Driver"
    )

private fun getEnvVar(envar: String) = System.getenv(envar) ?: error("missing envvar $envar")

data class JoarkConfig(
    val baseUrl: String = getEnvVar("JOARK_BASE_URL")
)

data class TokenXConfig(
    val clientId: String = getEnvVar("TOKEN_X_CLIENT_ID"),
    val issuer: String = getEnvVar("TOKEN_X_ISSUER"),
    val jwks: String = getEnvVar("TOKEN_X_JWKS_URI")
)
