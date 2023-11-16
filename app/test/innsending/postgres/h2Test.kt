package innsending.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class h2Test {
    private val dataSource: DataSource
    private val flyway: Flyway
    private val innsendingDAO: InnsendingDAO

    init {
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:request_no;MODE=PostgreSQL"
            username = "sa"
            password = ""
            maximumPoolSize = 3
        })
        println(MigrationVersion.CURRENT)
        flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:request_no;MODE=PostgreSQL","sa","")
            .load()
            .apply { migrate() }
        innsendingDAO = InnsendingDAO(dataSource)
    }

    @Test
    fun `Inserter en innsending`() {
        val søknadId = UUID.randomUUID()
        innsendingDAO.insertInnsending(søknadId, "12345678910", "søknad".toByteArray())

        Assertions.assertEquals(1, countInnsending())
    }


    internal fun countInnsending(): Int? =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT count(*) FROM innsending")
                .use { preparedStatement ->
                    val resultSet = preparedStatement.executeQuery()

                    resultSet.map { row ->
                        row.getInt(1)
                    }.singleOrNull()
                }
        }

    internal fun countVedlegg(): Int? =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT count(*) FROM fil")
                .use { preparedStatement ->
                    val resultSet = preparedStatement.executeQuery()

                    resultSet.map { row ->
                        row.getInt(1)
                    }.singleOrNull()
                }
        }
}