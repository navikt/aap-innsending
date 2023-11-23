package innsending.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import innsending.PostgresConfig
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*
import java.util.logging.Logger
import javax.sql.DataSource

private val logger = Logger.getLogger("App")
private const val DISABLE_FLYWAY_CLEAN = false
private const val ENABLE_FLYWAY_CLEAN_ON_VALIDATION_ERROR = true

internal object Hikari {
    fun createDatasource(config: PostgresConfig): DataSource =
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            maximumPoolSize = 3
            minimumIdle = 1
            initializationFailTimeout = 5000
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
            driverClassName = config.driver
        })

    fun flywayMigration(ds: DataSource) {
        if (!DISABLE_FLYWAY_CLEAN) logger.warning("Flyway.cleanDisabled er satt til false. Husk å skru av i prod")
        if (ENABLE_FLYWAY_CLEAN_ON_VALIDATION_ERROR) logger.warning("Flyway.cleanOnValidationError er satt til true. Husk å skru av i prod")

        Flyway
            .configure()
            .cleanDisabled(DISABLE_FLYWAY_CLEAN) // TODO: husk å skru av denne før prod
            .cleanOnValidationError(ENABLE_FLYWAY_CLEAN_ON_VALIDATION_ERROR) // TODO: husk å skru av denne før prod
            .dataSource(ds)
            .load()
            .migrate()
    }
}

fun <T : Any> ResultSet.map(block: (rs: ResultSet) -> T): List<T> =
    sequence {
        while (next()) yield(block(this@map))
    }.toList()

fun <T> Connection.transaction(block: (connection: Connection) -> T): T {
    return this.use { connection ->
        try {
            connection.autoCommit = false
            val result = block(this)
            connection.commit()
            result
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }
}

fun ResultSet.getUUID(columnLabel: String): UUID = UUID.fromString(this.getString(columnLabel))

fun PreparedStatement.setNullableObject(index: Int, obj: Any?, type: Int) {
    if (obj != null) {
        this.setObject(index, obj)
    } else {
        this.setNull(index, type)
    }
}
