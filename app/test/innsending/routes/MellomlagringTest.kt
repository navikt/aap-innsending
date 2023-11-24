package innsending.routes

import innsending.Fakes
import innsending.TokenXJwksGenerator
import innsending.TestConfig
import innsending.redis.JedisRedisFake
import innsending.server
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


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

    @Test
    fun `kan hente mellomlagring`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXJwksGenerator(config.tokenx)

            testApplication {
                application { server(config, jedis) }
                jedis.set("12345678910", """{"søknadId":"1234"}""".toByteArray())

                val res = client.get("/mellomlagring/søknad") {
                    accept(ContentType.Application.Json)
                    bearerAuth(jwkGen.generateTokenX("12345678910").serialize())
                }
                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals("""{"søknadId":"1234"}""", res.bodyAsText())
            }
        }
    }


    @Test
    fun `kan slette mellomlagring`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXJwksGenerator(config.tokenx)

            testApplication {
                application { server(config, jedis) }
                jedis.set("12345678910", """{"søknadId":"1234"}""".toByteArray())

                val del = client.delete("/mellomlagring/søknad") {
                    accept(ContentType.Application.Json)
                    bearerAuth(jwkGen.generateTokenX("12345678910").serialize())
                }
                assertEquals(HttpStatusCode.OK, del.status)

                assertNull(jedis.get("12345678910"))
            }
        }
    }

    @Test
    fun `ingen mellomlagring returnerer 404`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXJwksGenerator(config.tokenx)

            testApplication {
                application { server(config, jedis) }

                val res = client.get("/mellomlagring/søknad") {
                    accept(ContentType.Application.Json)
                    bearerAuth(jwkGen.generateTokenX("12345678910").serialize())
                }
                assertEquals(res.status, HttpStatusCode.NotFound)
            }
        }
    }
}
