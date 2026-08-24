package innsending

import innsending.kafka.KafkaConfig

internal object TestConfig {

    internal val postgres = PostgresConfig(
        host = "stub",
        port = "5432",
        database = "test_db",
        username = "sa",
        password = "",
        url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
        driver = "org.h2.Driver"
    )

    private val redis = RedisConfig(
        uri = InitTestRedis.uri,
        username = "test",
        password = "test"
    )

    fun default(fakes: Fakes): Config {
        return Config(
            maxFileSize = 50,
            postgres = postgres,
            redis = redis,
            joark = JoarkConfig(
                baseUrl = "http://localhost:${fakes.joark.port}",
                scope = "api://dev-fss.teamdokumenthandtering.dokarkiv/.default"
            ),
            pdfGenHost = "http://localhost:${fakes.pdfGen.port()}",
            pdfGeneratorHost = "http://localhost:${fakes.pdfGen.port()}",
            virusScanHost = "http://localhost:${fakes.virusScan.port()}",
            kafka = KafkaConfig(
                brokers = "localhost",
                truststorePath = "test",
                keystorePath = "test",
                credstorePsw = "test"
            ),
            oppslag = OppslagConfig(
                host = "http://localhost:${fakes.oppslag.port()}",
                scope = "api://dev-gcp.aap.oppslag/.default"
            ),
            unleash = UnleashConfig(
                apiUrl = "http://localhost",
                apiToken = "test",
                environment = "test"
            ),
            teamAapRolle = "nais-team-aap"
        )
    }
}