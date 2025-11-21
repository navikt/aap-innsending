package innsending

import com.zaxxer.hikari.HikariDataSource
import innsending.antivirus.ClamAVClient
import innsending.auth.TOKENX
import innsending.auth.authentication
import innsending.jobb.ArkiverInnsendingJobbUtfører
import innsending.jobb.MinSideNotifyJobbUtfører
import innsending.kafka.KafkaProducer
import innsending.kafka.MinSideKafkaProducer
import innsending.kafka.MinSideProducerHolder
import innsending.pdf.PdfGen
import innsending.postgres.Hikari
import innsending.redis.Redis
import innsending.routes.actuator
import innsending.routes.innsendingRoute
import innsending.routes.mellomlagerRoute
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.Motor
import no.nav.aap.motor.retry.RetryService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

val logger: Logger = LoggerFactory.getLogger("App")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        logger.error(
            "Uhåndtert feil. Type: ${e.javaClass}.",
            e
        )
    }
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

fun Application.server(
    config: Config = Config(),
    redis: Redis = Redis(config.redis),
    minsideProducer: KafkaProducer = MinSideKafkaProducer(config.kafka),
    datasource: HikariDataSource = Hikari.createAndMigrate(
        config.postgres,
        meterRegistry = prometheus.prometheus
    ),
) {
    val prometheus = prometheus.prometheus
    val antivirus = ClamAVClient(config.virusScanHost)
    val pdfGen = PdfGen(config)

    MinSideProducerHolder.setProducer(minsideProducer)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    authentication(config.tokenx)

    install(CallLogging) {
        level = Level.TRACE
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
            logger.error(
                "Uhåndtert feil ved kall til '{}'. Type: ${cause.javaClass}. Har root cause: ${cause.cause != null}.",
                call.request.local.uri,
                cause
            )
            call.respondText(
                text = "Feil i tjeneste: ${cause.message}",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    install(ContentNegotiation) {
        register(
            ContentType.Application.Json,
            JacksonConverter(objectMapper = DefaultJsonMapper.objectMapper(), true)
        )
    }

    module(datasource, minsideProducer, redis, prometheus)

    routing {
        authenticate(TOKENX) {
            innsendingRoute(datasource, redis, prometheus, config.maxFileSize)
            mellomlagerRoute(redis, antivirus, pdfGen, config.maxFileSize)
        }

        actuator(prometheus, redis)
    }
}

fun Application.module(
    dataSource: HikariDataSource,
    minsideProducer: KafkaProducer,
    redis: Redis,
    prometheus: PrometheusMeterRegistry
): Motor {
    val motor = Motor(
        dataSource = dataSource,
        antallKammer = 2,
        jobber = ProsesseringsJobber.alle(),
        prometheus = prometheus
    )

    dataSource.transaction { dbConnection ->
        RetryService(dbConnection).enable()
    }

    monitor.subscribe(ApplicationStarted) {
        motor.start()
    }
    monitor.subscribe(ApplicationStopPreparing) {
        motor.stop()
    }
    monitor.subscribe(ApplicationStopping) {
        minsideProducer.close()
        redis.close()
        dataSource.close()
    }
    monitor.subscribe(ApplicationStopped) { application ->
        application.environment.log.info("Server har stoppet")
    }

    return motor
}

object ProsesseringsJobber {

    fun alle(): List<Jobb> {
        // Legger her alle oppgavene som skal utføres i systemet
        return listOf(
            ArkiverInnsendingJobbUtfører,
            MinSideNotifyJobbUtfører
        )
    }
}
