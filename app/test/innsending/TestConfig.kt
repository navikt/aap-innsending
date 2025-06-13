package innsending

import innsending.kafka.KafkaConfig
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import java.net.URI

internal object TestConfig {

    internal val postgres = PostgresConfig(
        host = "stub",
        port = "5432",
        database = "test_db",
        username = "sa",
        password = "",
        url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
        driver = "org.h2.Driver"
    )

    private val redis = RedisConfig(
        uri = URI.create("http://127.0.0.1:6379"),
        username = "test",
        password = "test"
    )

    fun default(fakes: Fakes): Config {
        return Config(
            maxFileSize = 50,
            postgres = postgres,
            redis = redis,
            joark = JoarkConfig(
                baseUrl = "http://localhost:${fakes.joark.port}",
                scope = "api://dev-fss.teamdokumenthandtering.dokarkiv/.default"
            ),
            pdfGenHost = "http://localhost:${fakes.pdfGen.port()}",
            virusScanHost = "http://localhost:${fakes.virusScan.port()}",
            azure = AzureConfig(
                tokenEndpoint = URI.create("http://localhost:${fakes.azure.port()}/token"),
                clientId = "test",
                clientSecret = "test",
                jwksUri = "test",
                issuer = "test",
            ),
            tokenx = TokenXConfig(
                clientId = "aap-innsending",
                issuer = "tokenx",
                jwks = URI.create("http://localhost:${fakes.tokenx.port()}/jwks")
            ),
            kafka = KafkaConfig(
                brokers = "localhost",
                truststorePath = "test",
                keystorePath = "test",
                credstorePsw = "test"
            ),
            oppslag = OppslagConfig(
                host = "http://localhost:${fakes.oppslag.port()}",
                scope = "api://dev-gcp.aap.oppslag/.default"
            ),
        )
    }
}