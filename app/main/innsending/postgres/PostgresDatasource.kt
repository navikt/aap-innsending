package innsending.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import innsending.PostgresConfig
import io.micrometer.core.instrument.MeterRegistry
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*
import javax.sql.DataSource

internal object Hikari {
    fun createAndMigrate(
        config: PostgresConfig,
        locations: Array<String> = arrayOf("classpath:db/migration", "classpath:db/gcp"),
        meterRegistry: MeterRegistry? = null
    ): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            maximumPoolSize = 3
            minimumIdle = 1
            initializationFailTimeout = 5000
            idleTimeout = 10001
            connectionTimeout = 1000
            driverClassName = config.driver
            metricRegistry = meterRegistry
        }

        return createAndMigrate(hikariConfig, locations)
    }

    fun createAndMigrate(
        config: HikariConfig,
        locations: Array<String>
    ): DataSource {
        val dataSource = HikariDataSource(config)

        Flyway
            .configure()
            .dataSource(dataSource)
            .locations(*locations)
            .validateMigrationNaming(true)
            .load()
            .migrate()

        return dataSource
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
