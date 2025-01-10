package innsending.routes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import innsending.Fakes
import innsending.Resource
import innsending.TestConfig
import innsending.TokenXGen
import innsending.dto.ErrorRespons
import innsending.dto.MellomlagringRespons
import innsending.postgres.PostgresTestBase
import innsending.redis.EnDagSekunder
import innsending.redis.Key
import innsending.server
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MellomlagringTest : PostgresTestBase() {

    @Test
    fun `kan mellomlagre søknad`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
                fakes.redis.del(Key("12345678910"))

                val res = client.get("/mellomlagring/søknad") {
                    accept(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                }
                assertEquals(res.status, HttpStatusCode.NoContent)
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
                val key = Key(value = id.toString(), prefix = "12345678910")
                fakes.redis.set(key, String(Resource.read("/resources/pdf/minimal.pdf")).toByteArray(), 50)

                val res = client.get("/mellomlagring/fil/$id") {
                    accept(ContentType.Application.Pdf)
                    bearerAuth(tokenx.generate("12345678910"))
                }
                assertEquals(HttpStatusCode.OK, res.status)
                assertEquals(String(Resource.read("/resources/pdf/minimal.pdf")), String(res.readRawBytes()))
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
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
                assertEquals(String(Resource.read("/resources/pdf/minimal.pdf")), String(resHent.readRawBytes()))
            }
        }
    }

    @Test
    fun `expire på vedlegg blir utvidet ved oppdatering av søknad`() {
        Fakes().use { fakes ->
            val jedis = fakes.redis
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)
            val filId = UUID.randomUUID()
            val filId2 = UUID.randomUUID()

            testApplication {
                application { server(
                    config,
                    jedis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
                jedis.set(Key("12345678910"), """{"søknadId":"1234"}""".toByteArray(), 50)

                val key = Key(value = filId.toString(), prefix = "12345678910")
                jedis.set(key, String(Resource.read("/resources/pdf/minimal.pdf")).toByteArray(), 50)
                val key2 = Key(value = filId2.toString(), prefix = "12345678911")
                jedis.set(key2, String(Resource.read("/resources/pdf/minimal.pdf")).toByteArray(), 50)


                client.post("/mellomlagring/søknad") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                    setBody("""{"soknadId":"1234"}""")
                }

                assertThat(jedis.expiresIn(key)).isGreaterThan(50)
                assertThat(jedis.expiresIn(key2)).isLessThanOrEqualTo(50)
            }
        }
    }

    @Test
    fun `Sjekk om søknad finnes i mellomlager`() {
        Fakes().use { fakes ->
            val jedis = fakes.redis
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                val client = createClient {
                    install(ContentNegotiation) { jackson { registerModule(JavaTimeModule()) } }
                }
                application { server(
                    config,
                    jedis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
                jedis.del(Key("12345678910"))

                val resFørOpprettelse = client.get("/mellomlagring/søknad/finnes") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                }

                jedis.set(Key("12345678910"), """{"søknadId":"1234"}""".toByteArray(), EnDagSekunder)

                val resEtterOpprettelse = client.get("/mellomlagring/søknad/finnes") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(jwkGen.generate("12345678910"))
                }

                assertThat(resFørOpprettelse.status).isEqualTo(HttpStatusCode.NoContent)
                assertThat(resEtterOpprettelse.status).isEqualTo(HttpStatusCode.OK)
            }
        }
    }
}
