import innsending.*
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
            uri = "localhost:6379",
            username = "test",
            password = "test"
        ),
        azure = AzureConfig(
            tokenEndpoint = URI.create("localhost:$azurePort").toURL(),
            clientId = "test",
            clientSecret = "test"
        ),
        joark = JoarkConfig(
            baseUrl= "localhost:$joarkPort"
        ),
        tokenx = TokenXConfig(
            clientId = "test",
            wellknown = "localhost:$tokenXPort",
            issuer = "test",
            jwks = "localhost:$tokenXPort"
        ),
    )
  }
}