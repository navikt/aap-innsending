package innsending.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import javax.sql.DataSource


object InitH2 {
    val dataSource: DataSource
    private val flyway: Flyway

    init {
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:request_no;MODE=PostgreSQL"
            username = "sa"
            password = ""
            maximumPoolSize = 3
        })

        flyway = Flyway.configure()
            .dataSource(dataSource)
            .load()
            .apply { migrate() }
    }
}

abstract class H2TestBase {
    @BeforeEach
    fun clearTables() {
        InitH2.dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM innsending").use { preparedStatement ->
                preparedStatement.execute()
            }
        }
    }

    fun countInnsending(): Int? =
        InitH2.dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT count(*) FROM innsending")
                .use { preparedStatement ->
                    val resultSet = preparedStatement.executeQuery()

                    resultSet.map { row ->
                        row.getInt(1)
                    }.singleOrNull()
                }
        }

    fun countVedlegg(): Int? =
        InitH2.dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT count(*) FROM fil")
                .use { preparedStatement ->
                    val resultSet = preparedStatement.executeQuery()

                    resultSet.map { row ->
                        row.getInt(1)
                    }.singleOrNull()
                }
        }
}