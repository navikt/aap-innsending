package innsending.postgres

import com.zaxxer.hikari.HikariDataSource
import innsending.InitTestDatabase
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import javax.sql.DataSource

abstract class PostgresTestBase {
    protected val dataSource: HikariDataSource = Hikari.createAndMigrate(
        InitTestDatabase.hikariConfig,
        arrayOf("classpath:db/migration")
    )

    @BeforeEach
    fun clearTables() {
        dataSource.transaction { con ->
            con.prepareStatement("TRUNCATE TABLE fil, innsending, logg").execute()
        }
    }

    fun countInnsending(): Int? =
        dataSource.transaction { con ->
            val stmt = con.prepareStatement("SELECT count(*) FROM innsending")
            val resultSet = stmt.executeQuery()
            resultSet.map { row -> row.getInt(1) }.singleOrNull()
        }

    fun countFiler(): Int? =
        dataSource.transaction { con ->
            val stmt = con.prepareStatement("SELECT count(*) FROM fil")
            val resultSet = stmt.executeQuery()
            resultSet.map { row -> row.getInt(1) }.singleOrNull()
        }

    fun countLogg(): Int? =
        dataSource.transaction { con ->
            val stmt = con.prepareStatement("SELECT count(*) FROM logg")
            val resultSet = stmt.executeQuery()
            resultSet.map { row -> row.getInt(1) }.singleOrNull()
        }

    fun getAllInnsendinger(): List<UUID> =
        dataSource.transaction { con ->
            val stmt = con.prepareStatement("SELECT * FROM innsending")
            val result = stmt.executeQuery()
            result.map { it.getUUID("id") }
        }

    fun countInnsendingNy(): Int? =
        dataSource.transaction { con ->
            val stmt = con.prepareStatement("SELECT count(*) FROM innsending_ny")
            val resultSet = stmt.executeQuery()
            resultSet.map { row -> row.getInt(1) }.singleOrNull()
        }

    fun countFilerNy(): Int? =
        dataSource.transaction { con ->
            val stmt = con.prepareStatement("SELECT count(*) FROM fil_ny")
            val resultSet = stmt.executeQuery()
            resultSet.map { row -> row.getInt(1) }.singleOrNull()
        }

    fun getAllInnsendingerNy(): List<Long> =
        dataSource.transaction { con ->
            val stmt = con.prepareStatement("SELECT * FROM innsending_ny")
            val result = stmt.executeQuery()
            result.map { it.getLong("id") }
        }
}
