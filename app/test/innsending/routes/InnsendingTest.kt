package innsending

import innsending.postgres.H2TestBase
import innsending.postgres.Hikari
import innsending.postgres.PostgresRepo
import innsending.redis.JedisRedisFake
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotliquery.queryOf
import kotliquery.sessionOf
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource
import kotlin.test.assertEquals

class InnsendingTest: H2TestBase() {
    @Test
    fun `kan mellomlagre vedlegg`() {
        Fakes().use { fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)
            val jwkGen = TokenXJwksGenerator(config.tokenx)
            val vedleggId = UUID.randomUUID()
            val dataSource = Hikari.createDatasource(config.postgres)

            testApplication {
                application { server(config, jedis) }
                jedis.set(
                    vedleggId.toString(),
                    byteArrayOf()
                )

                val res = client.post("/innsending/søknad"){
                    bearerAuth(jwkGen.generateTokenX("12345678910").serialize())
                    contentType(ContentType.Application.Json)
                    setBody("""{
                                "soknad":"1234",
                                "vedlegg":[
                                    {
                                    "id":"${vedleggId}",
                                    "tittel":"tittel"
                                    }
                                ]
                                }""".trimMargin())
                }

                assertEquals(HttpStatusCode.OK, res.status)

                assertEquals(1, getInnsendingCount(dataSource))
                assertEquals(1, getVedleggCount(dataSource))
                val innsendinger = getAllInnsendinger(dataSource)
                assertEquals(1, innsendinger.size)

            }
        }
    }

    private fun getInnsendingCount(dataSource: DataSource):Int {
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT count(*) FROM innsending").map { it.int(1) }.asSingle
            )
        }!!
    }

    private fun getVedleggCount(dataSource: DataSource):Int{
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT count(*) FROM fil").map { it.int(1) }.asSingle
            )
        }!!
    }

    private fun getAllInnsendinger(dataSource: DataSource):List<UUID>{
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