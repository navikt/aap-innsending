package innsending

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import innsending.db.Repo
import innsending.fillager.FillagerClient
import innsending.kafka.Topics
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.kafka.streams.v2.KafkaStreams
import no.nav.aap.kafka.streams.v2.Streams
import no.nav.aap.kafka.streams.v2.Topology
import no.nav.aap.kafka.streams.v2.config.StreamsConfig
import no.nav.aap.ktor.client.AzureConfig
import no.nav.aap.ktor.config.loadConfig
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

private val secureLog = LoggerFactory.getLogger("secureLog")

data class Config(
    val kafka: StreamsConfig,
    val database: DbConfig,
    val azure: AzureConfig,
    val fillager: FillagerConfig
)

data class DbConfig(
    val url: String,
    val username: String,
    val password: String
)

data class FillagerConfig(
    val baseUrl: String
)

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
        route("/actuator") {
            get("/metrics") {
                call.respond(prometheus.scrape())
            }
            get("/live") {
                val status = if (kafka.live()) HttpStatusCode.OK else HttpStatusCode.InternalServerError
                call.respond(status, "vedtak")
            }
            get("/ready") {
                val status = if (kafka.ready()) HttpStatusCode.OK else HttpStatusCode.InternalServerError
                call.respond(status, "vedtak")
            }
        }

        route("/innsending") {
            route("/soknad") {
                get { /* Siste / alle / fullførte... */ }
                get("/{innsendingsreferanse}") { /*Henter ut en innsending data blob*/ }
                post {
                }
                post("/send_inn/{innsendingsreferanse}") {/* sender inn en søknad*/ }
                put("/{innsendingsreferanse}") {/*oppdaterer en innsending data blob(++)*/ }
            }
            delete("/{innsendingsreferanse}") {}
        }

        route("/fil") {
            get("/{filreferanse}") {
                call.respond(fillagerClient.hentFil(UUID.fromString(call.parameters["filreferanse"])))
            }

            post {
                call.respond(fillagerClient.opprettFil(call.receive()))
            }

            put("/{filreferanse}") { /* TODO: Endre metadata på en fil (tittel osv) */ }

            delete("/{filreferanse}") {
                fillagerClient.slettFil(UUID.fromString(call.parameters["filreferanse"]))
            }
        }
    }
}

internal fun topology(): Topology {
    return no.nav.aap.kafka.streams.v2.topology {
        consume(Topics.innsending)

    }
}

fun initDatasource(dbConfig: DbConfig) = HikariDataSource(HikariConfig().apply {
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

fun migrate(dataSource: DataSource) {
    Flyway
        .configure()
        .cleanDisabled(false) // TODO: husk å skru av denne før prod
        .cleanOnValidationError(true) // TODO: husk å skru av denne før prod
        .dataSource(dataSource)
        .locations("flyway")
        .load()
        .migrate()
}