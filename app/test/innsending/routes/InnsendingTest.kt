package innsending.routes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import innsending.Fakes
import innsending.TestConfig
import innsending.TokenXGen
import innsending.db.InnsendingNy
import innsending.db.InnsendingRepo
import innsending.dto.FilMetadata
import innsending.dto.Innsending
import innsending.dto.MineAapSoknadMedEttersendinger
import innsending.dto.MineAapSoknadMedEttersendingNy
import innsending.dto.ValiderFiler
import innsending.postgres.InnsendingType
import innsending.postgres.PostgresTestBase
import innsending.redis.Key
import innsending.server
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.aap.komponenter.dbconnect.transaction
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
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
                application { server(
                    config,
                    fakes.redis,
                    minsideProducer = fakes.kafka,
                    datasource = dataSource
                ) }
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

    @Test
    fun `kan hente søknader for bruker`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val personIdent = "12345678910"
            val ref1 = UUID.randomUUID()
            val ref2 = UUID.randomUUID()

            dataSource.transaction { con ->
                val repo = InnsendingRepo(con)
                listOf(ref1, ref2).forEach { ref ->
                    repo.lagre(
                        InnsendingNy(
                            id = null, opprettet = LocalDateTime.now(), personident = personIdent,
                            soknad = null, data = null, eksternRef = ref,
                            type = InnsendingType.SOKNAD, forrigeInnsendingId = null,
                            journalpost_Id = null, filer = emptyList()
                        )
                    )
                }
            }

            testApplication {
                application { server(config, fakes.redis, minsideProducer = fakes.kafka, datasource = dataSource) }

                val res = timeAwareClient.get("/innsending/søknader") {
                    bearerAuth(tokenx.generate(personIdent))
                }

                assertEquals(HttpStatusCode.OK, res.status)
                val body = res.body<List<MineAapSoknadMedEttersendingNy>>()
                assertThat(body).hasSize(2)
                assertThat(body.map { it.innsendingsId }).containsExactlyInAnyOrder(ref1, ref2)
            }
        }
    }

    @Test
    fun `kan hente søknadmedettersendinger for bruker`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val personIdent = "12345678910"
            val søknadRef = UUID.randomUUID()

            dataSource.transaction { con ->
                val repo = InnsendingRepo(con)
                val søknadId = repo.lagre(
                    InnsendingNy(
                        id = null, opprettet = LocalDateTime.now(), personident = personIdent,
                        soknad = null, data = null, eksternRef = søknadRef,
                        type = InnsendingType.SOKNAD, forrigeInnsendingId = null,
                        journalpost_Id = null, filer = emptyList()
                    )
                )
                repo.lagre(
                    InnsendingNy(
                        id = null, opprettet = LocalDateTime.now(), personident = personIdent,
                        soknad = null, data = null, eksternRef = UUID.randomUUID(),
                        type = InnsendingType.ETTERSENDING, forrigeInnsendingId = søknadId,
                        journalpost_Id = null, filer = emptyList()
                    )
                )
            }

            testApplication {
                application { server(config, fakes.redis, minsideProducer = fakes.kafka, datasource = dataSource) }

                val res = timeAwareClient.get("/innsending/søknadmedettersendinger") {
                    bearerAuth(tokenx.generate(personIdent))
                }

                assertEquals(HttpStatusCode.OK, res.status)
                val body = res.body<List<MineAapSoknadMedEttersendingNy>>()
                assertThat(body).hasSize(1)
                assertThat(body.first().innsendingsId).isEqualTo(søknadRef)
                assertThat(body.first().ettersendinger).hasSize(1)
            }
        }
    }

    @Test
    fun `kan hente ettersendinger for søknad`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val personIdent = "12345678910"
            val søknadRef = UUID.randomUUID()

            dataSource.transaction { con ->
                val repo = InnsendingRepo(con)
                val søknadId = repo.lagre(
                    InnsendingNy(
                        id = null, opprettet = LocalDateTime.now(), personident = personIdent,
                        soknad = null, data = null, eksternRef = søknadRef,
                        type = InnsendingType.SOKNAD, forrigeInnsendingId = null,
                        journalpost_Id = null, filer = emptyList()
                    )
                )
                repo.lagre(
                    InnsendingNy(
                        id = null, opprettet = LocalDateTime.now(), personident = personIdent,
                        soknad = null, data = null, eksternRef = UUID.randomUUID(),
                        type = InnsendingType.ETTERSENDING, forrigeInnsendingId = søknadId,
                        journalpost_Id = null, filer = emptyList()
                    )
                )
            }

            testApplication {
                application { server(config, fakes.redis, minsideProducer = fakes.kafka, datasource = dataSource) }

                val res = timeAwareClient.get("/innsending/søknader/$søknadRef/ettersendinger") {
                    bearerAuth(tokenx.generate(personIdent))
                }

                assertEquals(HttpStatusCode.OK, res.status)
                val body = res.body<MineAapSoknadMedEttersendinger>()
                assertThat(body.innsendingsId).isEqualTo(søknadRef)
                assertThat(body.ettersendinger).hasSize(1)
            }
        }
    }

    @Test
    fun `returnerer 404 for ukjent søknad referanse`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)

            testApplication {
                application { server(config, fakes.redis, minsideProducer = fakes.kafka, datasource = dataSource) }

                val res = client.get("/innsending/søknader/${UUID.randomUUID()}/ettersendinger") {
                    bearerAuth(tokenx.generate("12345678910"))
                }

                assertEquals(HttpStatusCode.NotFound, res.status)
            }
        }
    }

    @Test
    fun `valider-filer returnerer tom liste når alle filer finnes`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val personIdent = "12345678910"
            val filId1 = UUID.randomUUID().toString()
            val filId2 = UUID.randomUUID().toString()

            fakes.redis.set(Key(value = filId1, prefix = personIdent), byteArrayOf(1), 60)
            fakes.redis.set(Key(value = filId2, prefix = personIdent), byteArrayOf(1), 60)

            testApplication {
                application { server(config, fakes.redis, minsideProducer = fakes.kafka, datasource = dataSource) }

                val res = jsonHttpClient.post("/innsending/valider-filer") {
                    bearerAuth(tokenx.generate(personIdent))
                    contentType(ContentType.Application.Json)
                    setBody(ValiderFiler(filer = listOf(
                        FilMetadata(id = filId1, tittel = "fil1"),
                        FilMetadata(id = filId2, tittel = "fil2")
                    )))
                }

                assertEquals(HttpStatusCode.OK, res.status)
                assertThat(res.body<List<FilMetadata>>()).isEmpty()
            }
        }
    }

    @Test
    fun `valider-filer returnerer manglende filer`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val personIdent = "12345678910"
            val filIdFinnes = UUID.randomUUID().toString()
            val filIdMangler = UUID.randomUUID().toString()

            fakes.redis.set(Key(value = filIdFinnes, prefix = personIdent), byteArrayOf(1), 60)

            testApplication {
                application { server(config, fakes.redis, minsideProducer = fakes.kafka, datasource = dataSource) }

                val res = jsonHttpClient.post("/innsending/valider-filer") {
                    bearerAuth(tokenx.generate(personIdent))
                    contentType(ContentType.Application.Json)
                    setBody(ValiderFiler(filer = listOf(
                        FilMetadata(id = filIdFinnes, tittel = "fin1"),
                        FilMetadata(id = filIdMangler, tittel = "fin2")
                    )))
                }

                assertEquals(HttpStatusCode.OK, res.status)
                val manglende = res.body<List<FilMetadata>>()
                assertThat(manglende).hasSize(1)
                assertThat(manglende.first().id).isEqualTo(filIdMangler)
            }
        }
    }

    @Test
    fun `duplikat innsending returnerer 409`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes)
            val tokenx = TokenXGen(config.tokenx)
            val personIdent = "12345678910"
            val filId = Key(value = UUID.randomUUID().toString(), prefix = personIdent)
            fakes.redis.set(filId, byteArrayOf(), 60)

            val innsending = Innsending(filer = listOf(FilMetadata(id = filId.value, tittel = "vedlegg")))

            testApplication {
                application { server(config, fakes.redis, minsideProducer = fakes.kafka, datasource = dataSource) }

                val res1 = jsonHttpClient.post("/innsending") {
                    bearerAuth(tokenx.generate(personIdent))
                    contentType(ContentType.Application.Json)
                    setBody(innsending)
                }
                assertEquals(HttpStatusCode.OK, res1.status)

                // Hash er nå lagret i Redis — samme body skal gi 409
                val res2 = jsonHttpClient.post("/innsending") {
                    bearerAuth(tokenx.generate(personIdent))
                    contentType(ContentType.Application.Json)
                    setBody(innsending)
                }
                assertEquals(HttpStatusCode.Conflict, res2.status)
            }
        }
    }

    @Test
    fun `total filstørrelse over maks returnerer 412`() {
        Fakes().use { fakes ->
            val config = TestConfig.default(fakes).copy(maxFileSize = 1) // 1 MB grense
            val tokenx = TokenXGen(config.tokenx)
            val personIdent = "12345678910"
            val filId1 = Key(value = UUID.randomUUID().toString(), prefix = personIdent)
            val filId2 = Key(value = UUID.randomUUID().toString(), prefix = personIdent)
            val stor600KB = ByteArray(600 * 1024)
            fakes.redis.set(filId1, stor600KB, 60)
            fakes.redis.set(filId2, stor600KB, 60)

            testApplication {
                application { server(config, fakes.redis, minsideProducer = fakes.kafka, datasource = dataSource) }

                val res = jsonHttpClient.post("/innsending") {
                    bearerAuth(tokenx.generate(personIdent))
                    contentType(ContentType.Application.Json)
                    setBody(Innsending(
                        filer = listOf(
                            FilMetadata(id = filId1.value, tittel = "fil1"),
                            FilMetadata(id = filId2.value, tittel = "fil2")
                        )
                    ))
                }

                assertEquals(HttpStatusCode.PreconditionFailed, res.status)
            }
        }
    }

    private val ApplicationTestBuilder.jsonHttpClient: HttpClient
        get() =
            createClient {
                install(ContentNegotiation) { jackson() }
            }

    private val ApplicationTestBuilder.timeAwareClient: HttpClient
        get() =
            createClient {
                install(ContentNegotiation) { jackson { registerModule(JavaTimeModule()) } }
            }
}
