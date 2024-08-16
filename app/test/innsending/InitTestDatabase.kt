package innsending

import com.zaxxer.hikari.HikariConfig
import org.testcontainers.containers.PostgreSQLContainer

object InitTestDatabase {
    val hikariConfig: HikariConfig

    init {
        val postgres = PostgreSQLContainer<Nothing>("postgres:16")
        postgres.start()
        val jdbcUrl = postgres.jdbcUrl
        val username = postgres.username
        val password = postgres.password

        hikariConfig = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            minimumIdle = 1
            initializationFailTimeout = 30000
            idleTimeout = 10000
            connectionTimeout = 10000
            maxLifetime = 900000
            connectionTestQuery = "SELECT 1"
        }
    }
}
