package innsending.postgres

import innsending.InitTestDatabase
import org.junit.jupiter.api.BeforeEach
import java.util.*
import javax.sql.DataSource

abstract class H2TestBase {
    private val postgresContainer = InitTestDatabase
    protected val h2: DataSource = Hikari.createAndMigrate(
        postgresContainer.postgresConfig,
        arrayOf("classpath:db/migration")
    )

    @BeforeEach
    fun clearTables() {
        h2.transaction { con ->
            con.prepareStatement("TRUNCATE TABLE fil, innsending, logg").execute()
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
