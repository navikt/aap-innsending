package innsending

import com.zaxxer.hikari.HikariConfig
import org.testcontainers.containers.PostgreSQLContainer

object InitTestDatabase {
    val hikariConfig: HikariConfig

    init {
        var password = "postgres"
        var username = "postgres"
        var jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            val postgres = PostgreSQLContainer<Nothing>("postgres:16")
            postgres.start()
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
        }
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
