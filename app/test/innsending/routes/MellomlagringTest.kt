package innsending.routes

import innsending.*
import innsending.dto.ErrorRespons
import innsending.dto.MellomlagringRespons
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
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, fakes.redis, h2, fakes.kafka) }
                val res = client.post("/mellomlagring/søknad") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                    setBody("""{"soknadId":"1234"}""")
                }
                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals("""{"soknadId":"1234"}""", String(fakes.redis[Key("12345678910")]!!))
            }
        }
    }

    @Test
    fun `kan hente mellomlagring søknad`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, fakes.redis, h2, fakes.kafka) }
                fakes.redis.set(Key("12345678910"), """{"søknadId":"1234"}""".toByteArray(), 50)

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
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, fakes.redis, h2, fakes.kafka) }
                fakes.redis.set(Key("12345678910"), """{"søknadId":"1234"}""".toByteArray(), 50)

                val del = client.delete("/mellomlagring/søknad") {
                    accept(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                }
                assertEquals(HttpStatusCode.OK, del.status)

                assertNull(fakes.redis[Key("12345678910")])
            }
        }
    }

    @Test
    fun `ingen mellomlagring returnerer 404`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, fakes.redis, h2, fakes.kafka) }

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
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)
            testApplication {
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }
                application { server(config, fakes.redis, h2, fakes.kafka) }
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
                assertEquals(String(Resource.read("/resources/pdf/minimal.pdf")), fakes.redis[key]?.let(::String))
            }
        }
    }

    @Test
    fun `kan ikke mellomlagre stor fil`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)
            testApplication {
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }
                application { server(config, fakes.redis, h2, fakes.kafka) }
                val res = client.submitFormWithBinaryData(url = "/mellomlagring/fil",
                    formData = formData {
                        append("document", Resource.read("/resources/pdf/53mb.pdf"), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=53mb.pdf")
                            append(HttpHeaders.ContentType, "application/pdf")
                        })
                    },
                    block = {
                        bearerAuth(jwkGen.generate("12345678910"))
                    }
                )
                assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
                assertEquals(ErrorRespons("Filen 53mb.pdf er større enn maksgrense på 50MB"), res.body())
            }
        }
    }

    @Test
    fun `kan ikke mellomlagre tom pdf`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)
            testApplication {
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }
                application { server(config, fakes.redis, h2, fakes.kafka) }
                val res = client.submitFormWithBinaryData(url = "/mellomlagring/fil",
                    formData = formData {
                        append("document", Resource.read("/resources/pdf/tom.pdf"), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=tom.pdf")
                            append(HttpHeaders.ContentType, "application/pdf")
                        })
                    },
                    block = {
                        bearerAuth(jwkGen.generate("12345678910"))
                    }
                )
                assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
            }
        }
    }

    @Test
    fun `kan ikke mellomlagre tom png`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)
            testApplication {
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }
                application { server(config, fakes.redis, h2, fakes.kafka) }
                val res = client.submitFormWithBinaryData(url = "/mellomlagring/fil",
                    formData = formData {
                        append("document", Resource.read("/resources/images/tom.png"), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=tom.png")
                            append(HttpHeaders.ContentType, "image/png")
                        })
                    },
                    block = {
                        bearerAuth(jwkGen.generate("12345678910"))
                    }
                )
                assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
            }
        }
    }

    @Test
    fun `kan hente mellomlagret fil`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val id = UUID.randomUUID()
            testApplication {
                application { server(config, fakes.redis, h2, fakes.kafka) }
                val key = Key(value = id.toString(), prefix = "12345678910")
                fakes.redis.set(key, String(Resource.read("/resources/pdf/minimal.pdf")).toByteArray(), 50)

                val res = client.get("/mellomlagring/fil/$id") {
                    accept(ContentType.Application.Pdf)
                    bearerAuth(tokenx.generate("12345678910"))
                }
                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals(String(Resource.read("/resources/pdf/minimal.pdf")), String(res.readBytes()))
            }
        }
    }

    @Test
    fun `kan mellomlagre og hente fil`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            testApplication {
                val client = createClient {
                    install(ContentNegotiation) { jackson() }
                }
                application { server(config, fakes.redis, h2, fakes.kafka) }
                val resLagre = client.submitFormWithBinaryData(url = "/mellomlagring/fil",
                    formData = formData {
                        append("document", Resource.read("/resources/images/bilde.jpg"), Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=bilde.jpg")
                            append(HttpHeaders.ContentType, "image/jpeg")
                        })
                    },
                    block = {
                        bearerAuth(tokenx.generate("12345678910"))
                    }
                )


                assertEquals(HttpStatusCode.Created, resLagre.status)
                val respons = resLagre.body<MellomlagringRespons>()

                val resHent = client.get("/mellomlagring/fil/${respons.filId}") {
                    accept(ContentType.Application.Pdf)
                    bearerAuth(tokenx.generate("12345678910"))
                }
                assertEquals(HttpStatusCode.OK, resHent.status)
                assertEquals(String(Resource.read("/resources/pdf/minimal.pdf")), String(resHent.readBytes()))
            }
        }
    }

    @Test
    fun `expire på vedlegg blir utvidet ved oppdatering av søknad`(){
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)
            val filId = UUID.randomUUID()
            val filId2 = UUID.randomUUID()

            testApplication {
                application { server(config, jedis, h2, fakes.kafka) }
                jedis.set(Key("12345678910"), """{"søknadId":"1234"}""".toByteArray(), 50)

                val key = Key(value = filId.toString(), prefix = "12345678910")
                jedis.set(key, String(Resource.read("/resources/pdf/minimal.pdf")).toByteArray(), 50)
                val key2 = Key(value = filId2.toString(), prefix = "12345678911")
                jedis.set(key2, String(Resource.read("/resources/pdf/minimal.pdf")).toByteArray(), 50)


                val res2 = client.post("/mellomlagring/søknad") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                    setBody("""{"soknadId":"1234"}""")
                }

                assert(jedis.expiresIn(key) > 50)
                assert(jedis.expiresIn(key2) < 50)

            }
        }
    }
}
