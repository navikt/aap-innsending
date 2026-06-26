package innsending

import innsending.antivirus.ClamAVClient
import innsending.auth.TOKENX
import innsending.auth.authentication
import innsending.db.FilNy
import innsending.db.InMemoryFilData
import innsending.db.InnsendingNy
import innsending.db.InnsendingRepo
import innsending.dto.FilMetadata
import innsending.dto.Innsending
import innsending.jobb.ArkiverInnsendingJobbUtfører
import innsending.jobb.MinSideNotifyJobbUtfører
import innsending.kafka.KafkaProducer
import innsending.kafka.MinSideProducerHolder
import innsending.pdf.PdfGen
import innsending.postgres.Hikari
import innsending.redis.Key
import innsending.redis.Redis
import innsending.routes.actuator
import innsending.routes.innsendingRoute
import innsending.routes.mellomlagerRoute
import innsending.routes.toByteArray
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
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.JobbSpesifikasjon
import no.nav.aap.motor.Motor
import no.nav.aap.motor.retry.RetryService
import org.slf4j.event.Level
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

private const val PERSONIDENT = "08486725851"
fun main() {

    // Starter server
    embeddedServer(Netty, port = 8080, module = Application::testserver).start(wait = true)
}
fun Application.testserver(
    config: Config = TestConfig.default(Fakes()),
    redis: Redis = Redis(config.redis.uri),
    minsideProducer: KafkaProducer = Fakes().kafka,
    datasource: DataSource = Hikari.createAndMigrate(
                InitTestDatabase.hikariConfig,
        arrayOf("classpath:db/migration")
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
        route("/test/local-token") {
            get {
                val token = TokenXGen(config.tokenx).generate(PERSONIDENT)
                call.respond(token)
            }
        }

        authenticate(TOKENX) {
            innsendingRoute(datasource, redis, prometheus, config.maxFileSize)
            mellomlagerRoute(redis, antivirus, pdfGen, config.maxFileSize)
        }

        actuator(prometheus, redis)
    }
    datasource.transaction {
        opprettSøknadInnsendingMedFil(it)
    }
    // søknad med fil
}

fun Application.module(
    dataSource: DataSource,
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
private fun opprettSøknadInnsendingMedFil(connection: DBConnection){
    val randomId = UUID.randomUUID()
    val filId1 = Key(value = UUID.randomUUID().toString(), prefix = PERSONIDENT )
    val innsending = Innsending(
        kvittering = mapOf("kvittering" to "kvittering"),
        soknad = mapOf("søknad" to "søknad"),
        filer = listOf(
            FilMetadata(
                id = filId1.value,
                tittel = "important"
            )
        )
    )
    val filerMedInnhold = listOf<Pair<FilMetadata, ByteArray>>(Pair(FilMetadata("asdfasdf", "vedlegg"),
        "vedlegg".encodeToByteArray()
    ))
    val innsendingRepo = InnsendingRepo(connection)
    innsendingRepo.lagre(
        InnsendingNy(
            null,
            opprettet = LocalDateTime.now(),
            personident = PERSONIDENT,
            soknad = innsending.soknad?.toByteArray(),
            data = innsending.kvittering?.toByteArray(),
            eksternRef = randomId,
            type = innsending.type,
            journalpost_Id = null,
            forrigeInnsendingId = null,
            filer = filerMedInnhold.map { (metadata, byteArray) ->
                FilNy(
                    tittel = metadata.tittel,
                    data = InMemoryFilData(requireNotNull(byteArray))
                )
            }.toList()
        )
    )
}
