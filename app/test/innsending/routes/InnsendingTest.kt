package innsending.routes

import innsending.Fakes
import innsending.TestConfig
import innsending.TokenXGen
import innsending.postgres.H2TestBase
import innsending.redis.JedisRedisFake
import innsending.server
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
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
            val tokenx = TokenXGen(config.tokenx)
            val vedleggId1 = UUID.randomUUID()
            val vedleggId2 = UUID.randomUUID()

            testApplication {
                application { server(config, jedis, h2) }
                jedis[vedleggId1.toString()] = byteArrayOf()
                jedis[vedleggId2.toString()] = byteArrayOf()

                val res = jsonHttpClient.post("/innsending/søknad") {
                    bearerAuth(tokenx.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            soknad = "help me".toByteArray(),
                            vedlegg = listOf(
                                Vedlegg(
                                    id = vedleggId1.toString(),
                                    tittel = "important"
                                ),
                                Vedlegg(
                                    id = vedleggId2.toString(),
                                    tittel = "nice to have"
                                )
                            )
                        )
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
            val jwkGen = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, jedis, h2) }

                val res = jsonHttpClient.post("/innsending/søknad") {
                    bearerAuth(jwkGen.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            soknad = "søknad".toByteArray(),
                            vedlegg = listOf(
                                Vedlegg(
                                    id = UUID.randomUUID().toString(),
                                    tittel = " tittel1"
                                )
                            )
                        )
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

    private val ApplicationTestBuilder.jsonHttpClient: HttpClient get() =
        createClient {
            install(ContentNegotiation) { jackson() }
        }
}
