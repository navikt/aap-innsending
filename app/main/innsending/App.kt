package innsending

import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.apiRouting
import innsending.antivirus.ClamAVClient
import innsending.auth.AZURE
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
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import javax.sql.DataSource
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.JobbSpesifikasjon
import no.nav.aap.motor.Motor
import no.nav.aap.motor.api.motorApi
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
    datasource: DataSource = Hikari.createAndMigrate(
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

    authentication(azureConfig = config.azure, tokenxConfig = config.tokenx)

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

    // Nødvendig pga. motorApi
    install(OpenAPIGen) {
        serveOpenApiJson = false
        serveSwaggerUi = false
        api.info = InfoModel(title = "AAP - Innsending")
    }

    module(datasource, minsideProducer, redis, prometheus)

    routing {
        authenticate(TOKENX) {
            innsendingRoute(datasource, redis, prometheus, config.maxFileSize)
            mellomlagerRoute(redis, antivirus, pdfGen, config.maxFileSize)
        }

        authenticate(AZURE) {
            apiRouting {
                motorApi(datasource)
            }
        }

        actuator(prometheus, redis)
    }
}

fun Application.module(
    dataSource: DataSource,
    minsideProducer: KafkaProducer,
    redis: Redis,
    prometheus: PrometheusMeterRegistry,
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
    monitor.subscribe(ApplicationStopped) { application ->
        application.environment.log.info("Server har stoppet")
        motor.stop()
        minsideProducer.close()
        redis.close()
        // Release resources and unsubscribe from events
        application.monitor.unsubscribe(ApplicationStarted) {}
        application.monitor.unsubscribe(ApplicationStopped) {}
    }

    return motor
}

object ProsesseringsJobber {

    fun alle(): List<JobbSpesifikasjon> {
        // Legger her alle oppgavene som skal utføres i systemet
        return listOf(
            ArkiverInnsendingJobbUtfører,
            MinSideNotifyJobbUtfører
        )
    }
}
