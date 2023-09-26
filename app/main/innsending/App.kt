package innsending

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import innsending.db.Repo
import innsending.fillager.FillagerClient
import innsending.kafka.Topics
import innsending.routes.actuator
import innsending.routes.fil
import innsending.routes.innsending
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.kafka.streams.v2.KafkaStreams
import no.nav.aap.kafka.streams.v2.Streams
import no.nav.aap.kafka.streams.v2.Topology
import no.nav.aap.kafka.streams.v2.topology
import no.nav.aap.ktor.config.loadConfig
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val secureLog = LoggerFactory.getLogger("secureLog")

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

fun Application.server(kafka: Streams = KafkaStreams()) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val config = loadConfig<Config>()

    install(MicrometerMetrics) { registry = prometheus }

    Thread.currentThread().setUncaughtExceptionHandler { _, e -> secureLog.error("Uhåndtert feil", e) }
    environment.monitor.subscribe(ApplicationStopping) { kafka.close() }

    kafka.connect(
        config = config.kafka,
        registry = prometheus,
        topology = topology()
    )

    val datasource = initDatasource(config.database)
    migrate(datasource)
    val repo = Repo(datasource)
    val fillagerClient = FillagerClient(config.azure, config.fillager)

    routing {
        actuator(prometheus, kafka)

        innsending(repo)

        fil(fillagerClient)
    }
}

internal fun topology(): Topology {
    return topology {
        consume(Topics.innsending)

    }
}

private fun initDatasource(dbConfig: DbConfig) = HikariDataSource(HikariConfig().apply {
    jdbcUrl = dbConfig.url
    username = dbConfig.username
    password = dbConfig.password
    maximumPoolSize = 3
    minimumIdle = 1
    initializationFailTimeout = 5000
    idleTimeout = 10001
    connectionTimeout = 1000
    maxLifetime = 30001
    driverClassName = "org.postgresql.Driver"
})

private fun migrate(dataSource: DataSource) {
    Flyway
        .configure()
        .cleanDisabled(false) // TODO: husk å skru av denne før prod
        .cleanOnValidationError(true) // TODO: husk å skru av denne før prod
        .dataSource(dataSource)
        .locations("flyway")
        .load()
        .migrate()
}