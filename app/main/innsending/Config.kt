package innsending

import no.nav.aap.kafka.KafkaConfig
import no.nav.aap.ktor.client.AzureConfig
import java.net.URI

private fun getEnvVar(envar: String) = System.getenv(envar) ?: error("missing envvar $envar")

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
    val kafka: KafkaConfig = KafkaConfig(
        brokers = getEnvVar("KAFKA_BROKERS"),
        truststorePath = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
        keystorePath = getEnvVar("KAFKA_KEYSTORE_PATH"),
        credstorePsw = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
    ),
    val oppslag: OppslagConfig = OppslagConfig()
)

data class RedisConfig(
    val uri: URI = URI(getEnvVar("REDIS_URI_MELLOMLAGER").also { SECURE_LOGGER.info("redis uri $it") }),
    val username: String = getEnvVar("REDIS_USERNAME_MELLOMLAGER"),
    val password: String = getEnvVar("REDIS_PASSWORD_MELLOMLAGER"),
)

data class OppslagConfig(
    val host: String = "http://oppslag",
    val scope: String = getEnvVar("OPPSLAG_SCOPE")
)

data class PostgresConfig(
    val host: String = getEnvVar("NAIS_DATABASE_INNSENDING_INNSENDING_HOST"),
    val port: String = getEnvVar("NAIS_DATABASE_INNSENDING_INNSENDING_PORT"),
    val database: String = getEnvVar("NAIS_DATABASE_INNSENDING_INNSENDING_DATABASE"),
    val username: String = getEnvVar("NAIS_DATABASE_INNSENDING_INNSENDING_USERNAME"),
    val password: String = getEnvVar("NAIS_DATABASE_INNSENDING_INNSENDING_PASSWORD"),
    val url: String = "jdbc:postgresql://${host}:${port}/${database}",
    val driver: String = "org.postgresql.Driver",
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME")
)

data class JoarkConfig(
    val baseUrl: String = getEnvVar("JOARK_BASE_URL"),
    val scope: String = getEnvVar("JOARK_SCOPE")
)

data class TokenXConfig(
    val clientId: String = getEnvVar("TOKEN_X_CLIENT_ID"),
    val issuer: String = getEnvVar("TOKEN_X_ISSUER"),
    val jwks: URI = URI.create(getEnvVar("TOKEN_X_JWKS_URI"))
)
