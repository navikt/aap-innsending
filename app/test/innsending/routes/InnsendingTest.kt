package innsending.routes

import innsending.Fakes
import innsending.TestConfig
import innsending.TokenXGen
import innsending.dto.FilMetadata
import innsending.dto.Innsending
import innsending.postgres.PostgresDAO
import innsending.postgres.PostgresTestBase
import innsending.postgres.transaction
import innsending.redis.Key
import innsending.server
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

class InnsendingTest : PostgresTestBase() {

    @Test
    fun `kan sende inn søknad med 1 fil`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val filId1 = Key(value = UUID.randomUUID().toString(), prefix = "12345678910")
            val filId2 = Key(value = UUID.randomUUID().toString(), prefix = "12345678910")

            testApplication {
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
                fakes.redis.set(filId1, byteArrayOf(), 60)
                fakes.redis.set(filId2, byteArrayOf(), 60)

                val res = jsonHttpClient.post("/innsending") {
                    bearerAuth(tokenx.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            kvittering = mapOf("søknad" to "søknad"),
                            soknad = mapOf("søknad" to "søknad"),
                            filer = listOf(
                                FilMetadata(
                                    id = filId1.value,
                                    tittel = "important"
                                ),
                                FilMetadata(
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
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXGen(config.tokenx)
            println(JSONObject({ "søknad" }))
            testApplication {
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }

                val res = jsonHttpClient.post("/innsending") {
                    bearerAuth(jwkGen.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            soknad = mapOf("søknad" to "søknad"),
                            kvittering = mapOf("søknad" to "søknad"),
                            filer = listOf(
                                FilMetadata(
                                    id = UUID.randomUUID().toString(),
                                    tittel = " tittel1"
                                )
                            )
                        )
                    )
                }

                assertEquals(HttpStatusCode.PreconditionFailed, res.status)
                assertEquals(0, countInnsending())
                assertEquals(0, countFiler())
            }
        }
    }

    @Test
    fun `kan sende inn ettersending`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val filId1 = Key(UUID.randomUUID().toString(), prefix = "12345678910")
            val filId2 = Key(UUID.randomUUID().toString(), prefix = "12345678910")

            testApplication {
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
                fakes.redis.set(filId1, byteArrayOf(), 60)
                fakes.redis.set(filId2, byteArrayOf(), 60)

                val res = jsonHttpClient.post("/innsending") {
                    bearerAuth(tokenx.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            filer = listOf(
                                FilMetadata(
                                    id = filId1.value,
                                    tittel = "important"
                                ),
                                FilMetadata(
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
    fun `kan sende inn ettersending med soknadRef hvor soknad ikke er journalført`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val soknadRef = UUID.randomUUID()
            val filId1 = Key(UUID.randomUUID().toString(), prefix = "12345678910")
            val filId2 = Key(UUID.randomUUID().toString(), prefix = "12345678910")

            testApplication {
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
                fakes.redis.set(filId1, byteArrayOf(), 60)
                fakes.redis.set(filId2, byteArrayOf(), 60)

                dataSource.transaction { con ->
                    PostgresDAO.insertInnsending(
                        innsendingId = soknadRef,
                        personident = "12345678910",
                        mottattDato = LocalDateTime.now(),
                        soknad = null,
                        data = null,
                        con = con
                    )
                }

                val res = jsonHttpClient.post("/innsending/$soknadRef") {
                    bearerAuth(tokenx.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            filer = listOf(
                                FilMetadata(
                                    id = filId1.value,
                                    tittel = "important"
                                ),
                                FilMetadata(
                                    id = filId2.value,
                                    tittel = "nice to have"
                                )
                            )
                        )
                    )
                }

                assertEquals(HttpStatusCode.OK, res.status)

                assertEquals(2, countInnsending())
                assertEquals(2, countFiler())
                assertEquals(2, getAllInnsendinger().size)
            }
        }
    }

    @Test
    fun `kan sende inn ettersending med soknadRef hvor soknad er journalført`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val soknadRef = UUID.randomUUID()
            val filId1 = Key(UUID.randomUUID().toString(), prefix = "12345678910")
            val filId2 = Key(UUID.randomUUID().toString(), prefix = "12345678910")

            testApplication {
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
                fakes.redis.set(filId1, byteArrayOf(), 60)
                fakes.redis.set(filId2, byteArrayOf(), 60)

                dataSource.transaction { con ->
                    PostgresDAO.insertLogg(
                        innsendingId = soknadRef,
                        personident = "12345678910",
                        mottattDato = LocalDateTime.now(),
                        type = "SOKNAD",
                        journalpostId = "1234",
                        con = con
                    )
                }

                val res = jsonHttpClient.post("/innsending/$soknadRef") {
                    bearerAuth(tokenx.generate("12345678910"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        Innsending(
                            filer = listOf(
                                FilMetadata(
                                    id = filId1.value,
                                    tittel = "important"
                                ),
                                FilMetadata(
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
