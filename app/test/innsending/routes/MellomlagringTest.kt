package innsending.routes

import innsending.Fakes
import innsending.TokenXJwksGenerator
import innsending.TestConfig
import innsending.redis.JedisRedisFake
import innsending.server
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class MellomlagringTest {

    @Test
    fun `kan mellomlagre`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXJwksGenerator(config.tokenx)

            testApplication {
                application { server(config, jedis) }
                val res = client.post("/mellomlagring/søknad") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(jwkGen.generateTokenX("12345678910").serialize())
                    setBody("""{"soknadId":"1234"}""")
                }
                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals("""{"soknadId":"1234"}""", String(jedis["12345678910"]!!))
            }
        }
    }
}
