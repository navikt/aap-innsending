package innsending.postgres

import innsending.TestConfig
import org.junit.jupiter.api.BeforeEach
import java.util.*
import javax.sql.DataSource

abstract class H2TestBase {
    protected val h2: DataSource = Hikari.createDatasource(TestConfig.postgres).apply {
        Hikari.flywayMigration(this, "test")
    }

    @BeforeEach
    fun clearTables() {
        h2.transaction { con ->
            con.prepareStatement("SET REFERENTIAL_INTEGRITY FALSE").execute()
            con.prepareStatement("TRUNCATE TABLE innsending").execute()
            con.prepareStatement("TRUNCATE TABLE fil").execute()
            con.prepareStatement("SET REFERENTIAL_INTEGRITY TRUE").execute()
        }
    }

    fun countInnsending(): Int? =
        h2.transaction { con ->
            val stmt = con.prepareStatement("SELECT count(*) FROM innsending")
            val resultSet = stmt.executeQuery()
            resultSet.map { row -> row.getInt(1) }.singleOrNull()
        }

    fun countFiler(): Int? =
        h2.transaction { con ->
            val stmt = con.prepareStatement("SELECT count(*) FROM fil")
            val resultSet = stmt.executeQuery()
            resultSet.map { row -> row.getInt(1) }.singleOrNull()
        }

    fun countLogg(): Int? =
        h2.transaction { con ->
            val stmt = con.prepareStatement("SELECT count(*) FROM logg")
            val resultSet = stmt.executeQuery()
            resultSet.map { row -> row.getInt(1) }.singleOrNull()
        }

    fun getAllInnsendinger(): List<UUID> =
        h2.transaction { con ->
            val stmt = con.prepareStatement("SELECT * FROM innsending")
            val result = stmt.executeQuery()
            result.map { it.getUUID("id") }
        }
}
