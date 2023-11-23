package innsending

import no.nav.aap.ktor.client.AzureConfig
import java.net.URI

object TestConfig{
  fun default(azurePort:Int, tokenXPort:Int, joarkPort:Int):Config{
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
            tokenEndpoint = URI.create("http://127.0.0.1:$azurePort").toURL(),
            clientId = "test",
            clientSecret = "test"
        ),
        joark = JoarkConfig(
            baseUrl= "localhost:$joarkPort"
        ),
        tokenx = TokenXConfig(
            clientId = "aap-innsending",
            wellknown = "http://localhost:$tokenXPort/.well-known/jwks.json",
            issuer = "tokenx",
            jwks = "http://localhost:$tokenXPort"
        ),
    )
  }
}