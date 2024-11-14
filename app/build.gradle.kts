import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    id("io.ktor.plugin") version "3.0.1"
    application
}

val aapLibVersion = "5.0.24"
val ktorVersion = "3.0.1"

application {
    mainClass.set("innsending.AppKt")
}

dependencies {
    implementation("com.github.navikt.aap-libs:ktor-auth:$aapLibVersion")
    implementation("com.github.navikt.aap-libs:kafka:$aapLibVersion")
    implementation("org.apache.kafka:kafka-clients:3.9.0")

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
    implementation("org.apache.tika:tika-core:3.0.0")

    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("redis.clients:jedis:5.2.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.0")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.1")
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("com.zaxxer:HikariCP:6.0.0")
    implementation("org.flywaydb:flyway-core:10.21.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.21.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")
    runtimeOnly("org.postgresql:postgresql:42.7.4")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.41.1")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    constraints {
        implementation("org.apache.commons:commons-compress:1.27.1") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
    }
    testImplementation("org.testcontainers:postgresql:1.20.1")
    testImplementation("org.assertj:assertj-core:3.26.3")

}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        mergeServiceFiles()
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

kotlin.sourceSets["main"].kotlin.srcDirs("main")
kotlin.sourceSets["test"].kotlin.srcDirs("test")
sourceSets["main"].resources.srcDirs("main")
sourceSets["test"].resources.srcDirs("test")
