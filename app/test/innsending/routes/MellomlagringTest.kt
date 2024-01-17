package innsending.routes

import innsending.*
import innsending.postgres.H2TestBase
import innsending.redis.JedisRedisFake
import innsending.redis.Key
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MellomlagringTest: H2TestBase() {

    @Test
    fun `kan mellomlagre søknad`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, jedis, h2) }
                val res = client.post("/mellomlagring/søknad") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                    setBody("""{"soknadId":"1234"}""")
                }
                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals("""{"soknadId":"1234"}""", String(jedis[Key("12345678910")]!!))
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
                application { server(config, jedis, h2) }
                jedis.set(Key("12345678910"), """{"søknadId":"1234"}""".toByteArray(), 50)

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
                application { server(config, jedis, h2) }
                jedis.set(Key("12345678910"), """{"søknadId":"1234"}""".toByteArray(), 50)

                val del = client.delete("/mellomlagring/søknad") {
                    accept(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                }
                assertEquals(HttpStatusCode.OK, del.status)

                assertNull(jedis[Key("12345678910")])
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
                application { server(config, jedis, h2) }

                val res = client.get("/mellomlagring/søknad") {
                    accept(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                }
                assertEquals(res.status, HttpStatusCode.NotFound)
            }
        }
    }

    @Test
    fun `kan mellomlagre fil`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)
            testApplication {
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }
                application { server(config, jedis, h2) }
                val res = client.submitFormWithBinaryData(url = "/mellomlagring/fil",
                    formData = formData {
                        append("document", Resource.read("/resources/images/bilde.jpg"), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=bilde.jpg")
                            append(HttpHeaders.ContentType, "image/jpeg")
                        })
                    },
                    block = {
                        bearerAuth(jwkGen.generate("12345678910"))
                    }
                )
                assertEquals(HttpStatusCode.Created, res.status)
                val respons = res.body<MellomlagringRespons>()
                val key = Key(respons.filId, prefix = "12345678910")
                assertEquals(String(Resource.read("/resources/pdf/minimal.pdf")), jedis[key]?.let(::String))
            }
        }
    }

    @Test
    fun `kan hente mellomlagret fil`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val id = UUID.randomUUID()
            testApplication {
                application { server(config, jedis, h2) }
                val key = Key(value = id.toString(), prefix = "12345678910")
                jedis.set(key, String(Resource.read("/resources/pdf/minimal.pdf")).toByteArray(), 50)

                val res = client.get("/mellomlagring/fil/$id") {
                    accept(ContentType.Application.Pdf)
                    bearerAuth(tokenx.generate("12345678910"))
                }
                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals(String(Resource.read("/resources/pdf/minimal.pdf")), String(res.readBytes()))
            }
        }
    }
}
