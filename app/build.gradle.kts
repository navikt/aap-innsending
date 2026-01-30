import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("aap.conventions")
    id("io.ktor.plugin") version "3.4.0"
    application
}

val ktorVersion = "3.4.0"
val komponenterVersjon = "1.0.489"
val flywayVersjon = "11.20.3"
val behandlingsflytversjon = "0.0.537"

application {
    mainClass.set("innsending.AppKt")
}


dependencies {
    implementation("no.nav.aap.kelvin:json:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:httpklient:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:motor:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:dbconnect:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:infrastructure:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:motor-api:$komponenterVersjon")
    implementation("no.nav.aap.behandlingsflyt:kontrakt:$behandlingsflytversjon")

    implementation("org.apache.kafka:kafka-clients:4.1.1")
    implementation("no.nav.tms.mikrofrontend.selector:builder:20230704114948-74aa2e9")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")

    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("org.apache.tika:tika-core:3.2.3")

    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("redis.clients:jedis:7.2.1")
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.2")
    implementation("ch.qos.logback:logback-classic:1.5.26")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.6")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.flywaydb:flyway-core:$flywayVersjon")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersjon")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:9.0")
    runtimeOnly("org.postgresql:postgresql:42.7.9")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.7")
    testImplementation("com.redis:testcontainers-redis:2.2.4")
    constraints {
        implementation("org.apache.commons:commons-compress:1.28.0") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
    }
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.3")

    testImplementation("org.assertj:assertj-core:3.27.7")

}
tasks {
    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        duplicatesStrategy = DuplicatesStrategy.WARN
        mergeServiceFiles()
    }
}
