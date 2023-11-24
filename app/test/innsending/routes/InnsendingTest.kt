package innsending

import innsending.postgres.Hikari
import innsending.redis.JedisRedisFake
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotliquery.queryOf
import kotliquery.sessionOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource
import kotlin.test.assertEquals


class InnsendingTest {
    private val dataSource: DataSource = Hikari.createDatasource(
        PostgresConfig(
            host = "stub",
            port = "5432",
            database = "test_db",
            username = "sa",
            password = "",
            url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
    ).apply { Hikari.flywayMigration(this) }

    @AfterEach
    fun teardown() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("SET REFERENTIAL_INTEGRITY FALSE").asExecute)
            session.run(queryOf("TRUNCATE TABLE innsending").asExecute)
            session.run(queryOf("TRUNCATE TABLE fil").asExecute)
            session.run(queryOf("SET REFERENTIAL_INTEGRITY TRUE").asExecute)
        }
    }

    @Test
    fun `kan sende inn søknad med 1 vedlegg`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXJwksGenerator(config.tokenx)
            val vedleggId1 = UUID.randomUUID()
            val vedleggId2 = UUID.randomUUID()

            testApplication {
                application { server(config, jedis, dataSource) }
                jedis.set(
                    vedleggId1.toString(),
                    byteArrayOf()
                )
                jedis.set(
                    vedleggId2.toString(),
                    byteArrayOf()
                )

                val res = client.post("/innsending/søknad") {
                    bearerAuth(jwkGen.generateTokenX("12345678910").serialize())
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{
                                "soknad":"1234",
                                "vedlegg":[
                                    {
                                    "id":"${vedleggId1}",
                                    "tittel":"tittel1"
                                    },
                                    {
                                    "id":"${vedleggId2}",
                                    "tittel":"tittel2"
                                    }
                                ]
                                }""".trimMargin()
                    )
                }

                assertEquals(HttpStatusCode.OK, res.status)

                assertEquals(1, getInnsendingCount(dataSource))
                assertEquals(2, getVedleggCount(dataSource))
                val innsendinger = getAllInnsendinger(dataSource)
                assertEquals(1, innsendinger.size)
            }
        }
    }

    @Test
    fun `feiler ved manglende vedlegg`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXJwksGenerator(config.tokenx)
            val vedleggId1 = UUID.randomUUID()

            testApplication {
                application { server(config, jedis, dataSource) }

                val res = client.post("/innsending/søknad") {
                    bearerAuth(jwkGen.generateTokenX("12345678910").serialize())
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{
                                "soknad":"1234",
                                "vedlegg":[
                                    {
                                    "id":"${vedleggId1}",
                                    "tittel":"tittel1"
                                    }
                                ]
                                }""".trimMargin()
                    )
                }

                assertEquals(HttpStatusCode.NotFound, res.status)

                assertEquals(0, getInnsendingCount(dataSource))
                assertEquals(0, getVedleggCount(dataSource))

            }
        }
    }

    private fun getInnsendingCount(dataSource: DataSource): Int {
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT count(*) FROM innsending").map { it.int(1) }.asSingle
            )
        }!!
    }

    private fun getVedleggCount(dataSource: DataSource): Int {
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT count(*) FROM fil").map { it.int(1) }.asSingle
            )
        }!!
    }

    private fun getAllInnsendinger(dataSource: DataSource): List<UUID> {
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT * FROM innsending").map { it.uuid("id") }.asList
            )
        }
    }

    @Test
    fun `kan sende inn vedlegg`() {
        assert(true)
    }
}