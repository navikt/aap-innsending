package innsending.routes

import innsending.Fakes
import innsending.TestConfig
import innsending.TokenXGen
import innsending.postgres.H2TestBase
import innsending.redis.JedisRedisFake
import innsending.redis.Key
import innsending.server
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import org.json.JSONObject
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
            val filId1 = Key(value = UUID.randomUUID().toString(), prefix = "12345678910")
            val filId2 = Key(value = UUID.randomUUID().toString(), prefix = "12345678910")

            testApplication {
                application { server(config, jedis, h2) }
                jedis.set(filId1, byteArrayOf(), 60)
                jedis.set(filId2, byteArrayOf(), 60)

                val res = jsonHttpClient.post("/innsending") {
                    bearerAuth(tokenx.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            kvittering = mapOf("søknad" to "søknad"),
                            soknad = mapOf("søknad" to "søknad"),
                            filer = listOf(
                                Fil(
                                    id = filId1.value,
                                    tittel = "important"
                                ),
                                Fil(
                                    id = filId2.value,
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
            println(JSONObject({ "søknad" }))
            testApplication {
                application { server(config, jedis, h2) }

                val res = jsonHttpClient.post("/innsending") {
                    bearerAuth(jwkGen.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            kvittering = mapOf("søknad" to "søknad"),
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
            val filId1 = Key(UUID.randomUUID().toString(), prefix = "12345678910")
            val filId2 = Key(UUID.randomUUID().toString(), prefix = "12345678910")

            testApplication {
                application { server(config, jedis, h2) }
                jedis.set(filId1, byteArrayOf(), 60)
                jedis.set(filId2, byteArrayOf(), 60)

                val res = jsonHttpClient.post("/innsending") {
                    bearerAuth(tokenx.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            filer = listOf(
                                Fil(
                                    id = filId1.value,
                                    tittel = "important"
                                ),
                                Fil(
                                    id = filId2.value,
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

    private val ApplicationTestBuilder.jsonHttpClient: HttpClient
        get() =
            createClient {
                install(ContentNegotiation) { jackson() }
            }
}
