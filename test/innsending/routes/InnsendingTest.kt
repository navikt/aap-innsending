package innsending.routes

import innsending.Fakes
import innsending.TestConfig
import innsending.TokenXJwksGenerator
import innsending.postgres.H2TestBase
import innsending.redis.JedisRedisFake
import innsending.server
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class InnsendingTest : H2TestBase() {

    @Test
    fun `kan sende inn søknad med 1 vedlegg`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXJwksGenerator(config.tokenx)
            val vedleggId1 = UUID.randomUUID()
            val vedleggId2 = UUID.randomUUID()

            testApplication {
                application { server(config, jedis, h2) }
                jedis[vedleggId1.toString()] = byteArrayOf()
                jedis[vedleggId2.toString()] = byteArrayOf()

                val res = client.post("/innsending/søknad") {
                    bearerAuth(jwkGen.generateTokenX("12345678910").serialize())
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{
                                "soknad":"1234",
                                "vedlegg":[
                                    {
                                    "id":"$vedleggId1",
                                    "tittel":"tittel1"
                                    },
                                    {
                                    "id":"$vedleggId2",
                                    "tittel":"tittel2"
                                    }
                                ]
                                }""".trimMargin()
                    )
                }

                assertEquals(HttpStatusCode.OK, res.status)

                assertEquals(1, countInnsending())
                assertEquals(2, countVedlegg())
                assertEquals(1, getAllInnsendinger().size)
            }
        }
    }

    @Test
    fun `feiler ved manglende vedlegg`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXJwksGenerator(config.tokenx)

            testApplication {
                application { server(config, jedis, h2) }

                val res = client.post("/innsending/søknad") {
                    bearerAuth(jwkGen.generateTokenX("12345678910").serialize())
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{
                                "soknad":"1234",
                                "vedlegg":[
                                    {
                                    "id":"${UUID.randomUUID()}",
                                    "tittel":"tittel1"
                                    }
                                ]
                                }""".trimMargin()
                    )
                }

                assertEquals(HttpStatusCode.NotFound, res.status)
                assertEquals(0, countInnsending())
                assertEquals(0, countVedlegg())
            }
        }
    }

    @Test
    fun `kan sende inn vedlegg`() {
        assert(true)
    }
}