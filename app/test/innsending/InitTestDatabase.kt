package innsending

import org.testcontainers.containers.PostgreSQLContainer

object InitTestDatabase {
    val postgresConfig: PostgresConfig
    //val dataSource: DataSource

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
        postgresConfig = PostgresConfig(
            host = "",
            port = "5432",
            database = "test_db",
            username = username,
            password = password,
            url = jdbcUrl,
            cluster = "test"
        )
//        dataSource = HikariDataSource(HikariConfig().apply {
//            this.jdbcUrl = jdbcUrl
//            this.username = username
//            this.password = password
//            minimumIdle = 1
//            initializationFailTimeout = 30000
//            idleTimeout = 10000
//            connectionTimeout = 10000
//            maxLifetime = 900000
//            connectionTestQuery = "SELECT 1"
//        })
//
//        Flyway
//            .configure()
//            .cleanDisabled(false)
//            .cleanOnValidationError(true)
//            .dataSource(dataSource)
//            .locations("flyway")
//            .validateMigrationNaming(true)
//            .load()
//            .migrate()
    }
}
