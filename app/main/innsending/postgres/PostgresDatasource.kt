package innsending.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import innsending.PostgresConfig
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("App")

internal object Hikari {
    fun createAndMigrate(
        config: PostgresConfig,
        locations: Array<String> = arrayOf("classpath:db/migration", "classpath:db/gcp"),
    ): DataSource =
        HikariDataSource(
            HikariConfig().apply {
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
            }
        ).apply {
            Flyway
                .configure()
                .cleanDisabled(false).also { logger.error("flyway.cleanDisabled er false. Skru av hvis prod") }
                .cleanOnValidationError(true).also { logger.error("flyway.cleanOnValidationError er true. Skru av hvis prod") }
                .dataSource(this)
                .locations(*locations)
                .validateMigrationNaming(true)
                .load()
                .migrate()
        }
}

fun <T : Any> ResultSet.map(block: (ResultSet) -> T): List<T> =
    sequence {
        while (next()) yield(block(this@map))
    }.toList()

fun <T> DataSource.transaction(block: (Connection) -> T): T {
    return this.connection.use { connection ->
        try {
            connection.autoCommit = false
            val result = block(connection)
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
