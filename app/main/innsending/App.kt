package innsending

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import innsending.antivirus.ClamAVClient
import innsending.arkiv.JoarkClient
import innsending.arkiv.JournalpostSender
import innsending.auth.TOKENX
import innsending.auth.authentication
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
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.ktor.v2_0.server.KtorServerTracing
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import javax.sql.DataSource

val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLog")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> SECURE_LOGGER.error("Uhåndtert feil", e) }
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

fun Application.server(
    config: Config = Config(),
    redis: Redis = JedisRedis(config.redis),
    datasource: DataSource = Hikari.createAndMigrate(config.postgres),
    minsideProducer: KafkaProducer = MinSideKafkaProducer(config.kafka),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val antivirus = ClamAVClient(config.virusScanHost)
    val pdfGen = PdfGen(config.pdfGenHost)
    val postgres = PostgresRepo(datasource)

    val joarkClient = JoarkClient(config.azure, config.joark)
    val journalpostSender = JournalpostSender(joarkClient, postgres)
    val arkivScheduler = Apekatt(pdfGen, postgres, prometheus, journalpostSender, minsideProducer)

    environment.monitor.subscribe(ApplicationStopping) {
        arkivScheduler.close()
        minsideProducer.close()
    }

    install(MicrometerMetrics) { registry = prometheus }

    val otel = GlobalOpenTelemetry.get()
    install(KtorServerTracing) {
        setOpenTelemetry(otel)
    }

    authentication(config.tokenx)

    install(CallLogging) {
        level = Level.INFO
        logger = SECURE_LOGGER
        format { call ->
            """
                URL:            ${call.request.local.uri}
                Status:         ${call.response.status()}
                Method:         ${call.request.httpMethod.value}
                User-agent:     ${call.request.headers["User-Agent"]}
                CallId:         ${call.request.header("x-callId") ?: call.request.header("nav-callId")}
                Authorization:  ${call.request.header("Authorization")}
            """.trimIndent()
        }
        filter { call -> call.request.path().startsWith("/actuator").not() }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            SECURE_LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            call.respondText(text = "Feil i tjeneste: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
    }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        authenticate(TOKENX) {
            innsendingRoute(postgres, redis)
            mellomlagerRoute(redis, antivirus, pdfGen)
        }

        actuator(prometheus, redis)
    }
}
