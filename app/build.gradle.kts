plugins {
    id("aap.conventions")
    alias(kelvinLibs.plugins.ktor)
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
    implementation(kelvinLibs.kafka.clients)
    implementation(libs.tms.mikrofrontend.builder)
    implementation(kelvinLibs.unleash.client.java)

    implementation(kelvinLibs.ktor.client.cio)
    implementation(kelvinLibs.ktor.client.content.negotiation)
    implementation(kelvinLibs.ktor.client.jackson)
    implementation(kelvinLibs.ktor.client.logging)
    implementation(libs.tika.core)

    implementation(kelvinLibs.ktor.serialization.jackson)
    implementation(libs.jedis)
    implementation(kelvinLibs.micrometer.prometheus)
    implementation(kelvinLibs.logback.classic)
    implementation(kelvinLibs.jackson.datatype.jsr310)
    implementation(libs.pdfbox)
    implementation(kelvinLibs.hikaricp)
    implementation(kelvinLibs.flyway.postgresql)
    implementation(kelvinLibs.coroutines.core)
    runtimeOnly(kelvinLibs.logstash.logback.encoder)
    runtimeOnly(kelvinLibs.postgresql)

    testImplementation(kotlin("test"))
    testImplementation(kelvinLibs.ktor.server.test.host)
    testImplementation(kelvinLibs.nimbus.jose.jwt)
    testImplementation(libs.testcontainers.redis)
    testImplementation(kelvinLibs.testcontainers.postgresql)
    testImplementation(kelvinLibs.assertj.core)
}
tasks {
    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        duplicatesStrategy = DuplicatesStrategy.WARN
        mergeServiceFiles()
        // Sikrer at jar-en er byte-for-byte reproduserbar på tvers av maskiner/kjøringer
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
