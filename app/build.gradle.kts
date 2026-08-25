plugins {
    id("aap.conventions")
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("innsending.AppKt")
}

dependencies {
    implementation(libs.kelvin.json)
    implementation(libs.kelvin.httpklient)
    implementation(libs.kelvin.motor)
    implementation(libs.kelvin.server)
    implementation(libs.kelvin.dbconnect)
    implementation(libs.kelvin.infrastructure)
    implementation(libs.kelvin.motor.api)
    implementation(libs.kelvin.ktor.openapi.generator)
    implementation(libs.behandlingsflyt.kontrakt)
    implementation(libs.kafka.clients)
    implementation(libs.tms.mikrofrontend.builder)
    implementation(libs.unleash.client.java)

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.jackson)
    implementation(libs.ktor.client.logging)
    implementation(libs.tika.core)

    implementation(libs.ktor.serialization.jackson)
    implementation(libs.jedis)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.logback.classic)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.pdfbox)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.logstash.logback.encoder)
    runtimeOnly(libs.postgresql)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.nimbus.jose.jwt)
    testImplementation(libs.testcontainers.redis)
    constraints {
        implementation(libs.commons.compress) {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
    }
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.assertj.core)
}
tasks {
    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        duplicatesStrategy = DuplicatesStrategy.WARN
        mergeServiceFiles()
    }
}
