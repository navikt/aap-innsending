package innsending.routes

import com.fasterxml.jackson.databind.util.JSONPObject
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
    fun `kan sende inn søknad med 1 fil`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val filId1 = UUID.randomUUID()
            val filId2 = UUID.randomUUID()

            testApplication {
                application { server(config, jedis, h2) }
                jedis.set(filId1.toString(), byteArrayOf(), 60)
                jedis.set(filId2.toString(), byteArrayOf(), 60)

                val res = jsonHttpClient.post("/innsending") {
                    bearerAuth(tokenx.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            kvittering = "søknad",
                            filer = listOf(
                                Fil(
                                    id = filId1.toString(),
                                    tittel = "important"
                                ),
                                Fil(
                                    id = filId2.toString(),
                                    tittel = "nice to have"
                                )
                            )
                        )
                    )
                }

                assertEquals(HttpStatusCode.OK, res.status)

                assertEquals(1, countInnsending())
                assertEquals(2, countFiler())
                assertEquals(1, getAllInnsendinger().size)
            }
        }
    }

    @Test
    fun `feiler ved manglende filer`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)
            val filId1 = UUID.randomUUID()
            val filId2 = UUID.randomUUID()

            testApplication {
                application { server(config, jedis, h2) }

                val res = jsonHttpClient.post("/innsending") {
                    bearerAuth(jwkGen.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            kvittering = "søknad",
                            filer = listOf(
                                Fil(
                                    id = UUID.randomUUID().toString(),
                                    tittel = " tittel1"
                                )
                            )
                        )
                    )
                }

                assertEquals(HttpStatusCode.NotFound, res.status)
                assertEquals(0, countInnsending())
                assertEquals(0, countFiler())
            }
        }
    }

    @Test
    fun `kan sende inn ettersending`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val filId1 = UUID.randomUUID()
            val filId2 = UUID.randomUUID()

            testApplication {
                application { server(config, jedis, h2) }
                jedis.set(filId1.toString(), byteArrayOf(), 60)
                jedis.set(filId2.toString(), byteArrayOf(), 60)

                val res = jsonHttpClient.post("/innsending") {
                    bearerAuth(tokenx.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            filer = listOf(
                                Fil(
                                    id = filId1.toString(),
                                    tittel = "important"
                                ),
                                Fil(
                                    id = filId2.toString(),
                                    tittel = "nice to have"
                                )
                            )
                        )
                    )
                }

                assertEquals(HttpStatusCode.OK, res.status)

                assertEquals(1, countInnsending())
                assertEquals(2, countFiler())
                assertEquals(1, getAllInnsendinger().size)
            }
        }
    }

    private val ApplicationTestBuilder.jsonHttpClient: HttpClient get() =
        createClient {
            install(ContentNegotiation) { jackson() }
        }
}
