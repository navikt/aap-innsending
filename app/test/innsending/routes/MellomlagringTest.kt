package innsending.routes

import innsending.*
import innsending.redis.JedisRedisFake
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MellomlagringTest {

    @Test
    fun `kan mellomlagre søknad`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, jedis) }
                val res = client.post("/mellomlagring/søknad") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                    setBody("""{"soknadId":"1234"}""")
                }
                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals("""{"soknadId":"1234"}""", String(jedis["12345678910"]!!))
            }
        }
    }

    @Test
    fun `kan hente mellomlagring søknad`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, jedis) }
                jedis["12345678910"] = """{"søknadId":"1234"}""".toByteArray()

                val res = client.get("/mellomlagring/søknad") {
                    accept(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                }
                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals("""{"søknadId":"1234"}""", res.bodyAsText())
            }
        }
    }


    @Test
    fun `kan slette mellomlagret søknad`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, jedis) }
                jedis["12345678910"] = """{"søknadId":"1234"}""".toByteArray()

                val del = client.delete("/mellomlagring/søknad") {
                    accept(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                }
                assertEquals(HttpStatusCode.OK, del.status)

                assertNull(jedis["12345678910"])
            }
        }
    }

    @Test
    fun `ingen mellomlagring returnerer 404`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, jedis) }

                val res = client.get("/mellomlagring/søknad") {
                    accept(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                }
                assertEquals(res.status, HttpStatusCode.NotFound)
            }
        }
    }

    @Test
    fun `kan mellomlagre vedlegg`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, jedis) }
                val res = client.post("/mellomlagring/vedlegg") {
                    contentType(ContentType.Image.JPEG)
                    bearerAuth(jwkGen.generate("12345678910"))
                    setBody(Resource.read("/resources/images/bilde.jpg"))
                }
                assertEquals(HttpStatusCode.Created, res.status)
                assertEquals(String(Resource.read("/resources/pdf/minimal.pdf")), String(jedis[res.bodyAsText()]!!))
            }
        }
    }

    @Test
    fun `kan hente mellomlagret vedlegg`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val id = UUID.randomUUID()
            testApplication {
                application { server(config, jedis) }
                jedis[id.toString()] = String(Resource.read("/resources/pdf/minimal.pdf")).toByteArray()

                val res = client.get("/mellomlagring/vedlegg/$id") {
                    accept(ContentType.Application.Pdf)
                    bearerAuth(tokenx.generate("12345678910"))
                }
                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals(String(Resource.read("/resources/pdf/minimal.pdf")), String(res.readBytes()))
            }
        }
    }
}
