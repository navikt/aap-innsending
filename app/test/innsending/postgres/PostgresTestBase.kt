package innsending.postgres

import innsending.InitTestDatabase
import org.junit.jupiter.api.BeforeEach
import java.util.*
import javax.sql.DataSource

abstract class PostgresTestBase {
    protected val dataSource: DataSource = Hikari.createAndMigrate(
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
}
