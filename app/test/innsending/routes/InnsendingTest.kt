package innsending.routes

import com.fasterxml.jackson.databind.ObjectMapper
import innsending.Fakes
import innsending.TestConfig
import innsending.TokenXGen
import innsending.db.InnsendingNy
import innsending.db.InnsendingRepo
import innsending.dto.FilMetadata
import innsending.dto.Innsending
import innsending.postgres.InnsendingType
import innsending.postgres.PostgresTestBase
import innsending.redis.Key
import innsending.server
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import no.nav.aap.komponenter.dbconnect.transaction
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class InnsendingTest : PostgresTestBase() {

    @BeforeEach
    fun `truncate tables`() {
        dataSource.transaction { con ->
            con.execute("TRUNCATE innsending_ny CASCADE")
            con.execute("TRUNCATE fil_ny CASCADE")
        }
    }

    @Test
    fun `kan sende inn søknad med 1 fil`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val filId1 = Key(value = UUID.randomUUID().toString(), prefix = "12345678910")
            val filId2 = Key(value = UUID.randomUUID().toString(), prefix = "12345678910")

            testApplication {
                application {
                    server(
                        config,
                        fakes.redis,
                        minsideProducer = fakes.kafka,
                        datasource = dataSource
                    )
                }
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

                assertEquals(1, countInnsendingNy())
                assertEquals(2, countFilerNy())
                assertEquals(1, getAllInnsendingerNy().size)
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
                application {
                    server(
                        config,
                        fakes.redis,
                        minsideProducer = fakes.kafka,
                        datasource = dataSource
                    )
                }

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
                assertEquals(0, countInnsendingNy())
                assertEquals(0, countFilerNy())
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
                application {
                    server(
                        config,
                        fakes.redis,
                        minsideProducer = fakes.kafka,
                        datasource = dataSource
                    )
                }
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
                            ),
                            kvittering = ObjectMapper().readerFor(MutableMap::class.java).readValue(
                                """
                                    {"temaer":{"vedlegg":{"type":"TEMA","overskrift":"Ettersending","underblokker":[{"type":"FELT","felt":"Antall ettersendte dokumenter: 1"}]}}}
                                """.trimIndent()
                            )
                        )
                    )

                }

                assertEquals(HttpStatusCode.OK, res.status)

                assertEquals(1, countInnsendingNy())
                assertEquals(2, countFilerNy())
                assertEquals(1, getAllInnsendingerNy().size)
            }
        }
    }

    @Test
    fun `kan sende inn ettersending med soknadRef hvor soknad ikke er journalført`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val filId1 = Key(UUID.randomUUID().toString(), prefix = "12345678910")
            val filId2 = Key(UUID.randomUUID().toString(), prefix = "12345678910")

            val eksternRef = UUID.randomUUID()
            testApplication {
                application {
                    server(
                        config,
                        fakes.redis,
                        minsideProducer = fakes.kafka,
                        datasource = dataSource
                    )
                }
                fakes.redis.set(filId1, byteArrayOf(), 60)
                fakes.redis.set(filId2, byteArrayOf(), 60)

                dataSource.transaction { con ->
                    val innsendingRepo = InnsendingRepo(con)
                    innsendingRepo.lagre(
                        InnsendingNy(
                            id = 2L,
                            personident = "12345678910",
                            opprettet = LocalDateTime.now(),
                            soknad = null,
                            data = null,
                            eksternRef = eksternRef,
                            type = InnsendingType.ETTERSENDING,
                            forrigeInnsendingId = null,
                            journalpost_Id = null,
                            filer = emptyList()
                        )
                    )
                }

                val res = jsonHttpClient.post("/innsending/$eksternRef") {
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

                assertEquals(2, countInnsendingNy())
                assertEquals(2, countFilerNy())
                assertEquals(2, getAllInnsendingerNy().size)
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
                application {
                    server(
                        config,
                        fakes.redis,
                        minsideProducer = fakes.kafka,
                        datasource = dataSource
                    )
                }
                fakes.redis.set(filId1, byteArrayOf(), 60)
                fakes.redis.set(filId2, byteArrayOf(), 60)

                dataSource.transaction { con ->
                    val innsendingRepo = InnsendingRepo(con)
                    innsendingRepo.lagre(
                        InnsendingNy(
                            id = 1,
                            personident = "12345678910",
                            opprettet = LocalDateTime.now(),
                            soknad = null,
                            data = null,
                            eksternRef = soknadRef,
                            type = InnsendingType.SOKNAD,
                            forrigeInnsendingId = null,
                            journalpost_Id = null,
                            filer = emptyList()
                        )
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

                assertEquals(2, countInnsendingNy())
                assertEquals(2, countFilerNy())
                assertEquals(2, getAllInnsendingerNy().size)
            }
        }
    }

    private val ApplicationTestBuilder.jsonHttpClient: HttpClient
        get() =
            createClient {
                install(ContentNegotiation) { jackson() }
            }


}
