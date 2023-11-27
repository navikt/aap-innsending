package innsending.scheduler

import innsending.*
import innsending.TestConfig
import innsending.postgres.H2TestBase
import innsending.postgres.PostgresDAO
import innsending.postgres.transaction
import innsending.redis.JedisRedisFake
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals


class ArkivSchedulerTest: H2TestBase() {

    @Test
    private fun `should send journalpost`() {
        Fakes().use{ fakes ->
            val jedis = JedisRedisFake()
            val config = TestConfig.default(fakes)

            testApplication {
                application { server(config, jedis,h2) }
                h2.transaction {
                    PostgresDAO.insertInnsending(
                        UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                        "12345678910",
                        Resource.read("/resources/images/bilde.jpg"),
                        it
                    )
                }
                withTimeout(10000) {
                    while (
                        h2.transaction {
                            PostgresDAO.selectInnsendinger(it).isEmpty()
                        }
                    ) {
                        Thread.sleep(100)
                    }
                }

        }
    }
}