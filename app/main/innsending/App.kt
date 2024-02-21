package innsending

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import innsending.antivirus.ClamAVClient
import innsending.arkiv.JoarkClient
import innsending.arkiv.JournalpostSender
import innsending.auth.PersonidentException
import innsending.auth.TOKENX
import innsending.auth.authentication
import innsending.dto.ErrorCode
import innsending.dto.error
import innsending.kafka.KafkaProducer
import innsending.kafka.MinSideKafkaProducer
import innsending.pdf.PdfGen
import innsending.postgres.Hikari
import innsending.postgres.PostgresRepo
import innsending.redis.JedisRedis
import innsending.redis.Redis
import innsending.routes.actuator
import innsending.routes.innsendingRoute
import innsending.routes.mellomlagerRoute
import innsending.scheduler.Apekatt
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import javax.sql.DataSource

val SECURE_LOG: Logger = LoggerFactory.getLogger("secureLog")
val APP_LOG = LoggerFactory.getLogger("App")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> SECURE_LOG.error("Uhåndtert feil", e) }
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

fun Application.server(
    config: Config = Config(),
    redis: Redis = JedisRedis(config.redis),
    datasource: DataSource = Hikari.createAndMigrate(config.postgres),
    minsideProducer: KafkaProducer = MinSideKafkaProducer(config.kafka),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val antivirus = ClamAVClient(config, prometheus)
    val pdfGen = PdfGen(config, prometheus)
    val postgres = PostgresRepo(datasource)

    val joarkClient = JoarkClient(config, prometheus)
    val journalpostSender = JournalpostSender(joarkClient, postgres)
    val arkivScheduler = Apekatt(
        config,
        pdfGen,
        postgres,
        prometheus,
        journalpostSender,
        minsideProducer,
    ).apply {
        start()
    }

    environment.monitor.subscribe(ApplicationStopping) {
        runBlocking {
            delay(1000)
        }
        arkivScheduler.stop()
        minsideProducer.close()
        redis.close()
    }

    install(MicrometerMetrics) { registry = prometheus }

    authentication(config.tokenx)

    install(CallLogging) {
        level = Level.INFO
        format { call ->
            """
                URL:            ${call.request.local.uri}
                Status:         ${call.response.status()}
                Method:         ${call.request.httpMethod.value}
                User-agent:     ${call.request.headers["User-Agent"]}
                CallId:         ${call.request.header("x-callId") ?: call.request.header("nav-callId")}
            """.trimIndent()
        }
        filter { call -> call.request.path().startsWith("/actuator").not() }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is PersonidentException -> call.error(cause.error)
                else -> {
                    SECURE_LOG.error(
                        "Uhåndtert feil ved kall til '{}'",
                        call.request.local.uri,
                        cause
                    )

                    call.error(ErrorCode.UNKNOWN_ERROR)
                }
            }
        }
    }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }
    }

    routing {
        authenticate(TOKENX) {
            innsendingRoute(postgres, redis)
            mellomlagerRoute(redis, antivirus, pdfGen)
        }

        actuator(prometheus, redis)
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
    }
}
