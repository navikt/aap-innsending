package innsending

import no.nav.aap.ktor.client.AzureConfig
import java.net.URI

internal object TestConfig {
    fun default(fakes: Fakes): Config {
        return Config(
            postgres = PostgresConfig(
                host = "stub",
                port = "5432",
                database = "test_db",
                username = "sa",
                password = "",
                url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
                driver = "org.h2.Driver"
            ),
            redis = RedisConfig(
                uri = URI.create("http://127.0.0.1:6379"),
                username = "test",
                password = "test"
            ),
            azure = AzureConfig(
                tokenEndpoint = URI.create("http://127.0.0.1:${fakes.azure.port()}").toURL(),
                clientId = "test",
                clientSecret = "test"
            ),
            joark = JoarkConfig(
                baseUrl = "localhost:${fakes.joark.port()}"
            ),
            tokenx = TokenXConfig(
                clientId = "aap-innsending",
                issuer = "tokenx",
                jwks = "http://localhost:${fakes.tokenx.port()}"
            ),
            pdfGenHost = "http://localhost:${fakes.pdfGen.port()}",
            virusScanHost = "http://localhost:${fakes.virusScan.port()}"
        )
    }
}
