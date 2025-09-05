import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    id("io.ktor.plugin") version "3.2.3"
    application
}

val ktorVersion = "3.2.3"
val komponenterVersjon = "1.0.346"

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

    implementation("org.apache.kafka:kafka-clients:4.1.0")
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
    implementation("org.apache.tika:tika-core:3.2.2")

    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("redis.clients:jedis:6.2.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.3")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
    implementation("com.zaxxer:HikariCP:7.0.1")
    implementation("org.flywaydb:flyway-core:11.11.2")
    implementation("org.flywaydb:flyway-database-postgresql:11.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.1")
    runtimeOnly("org.postgresql:postgresql:42.7.7")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.4.2")
    testImplementation("com.redis:testcontainers-redis:2.2.4")
    constraints {
        implementation("org.apache.commons:commons-compress:1.28.0") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
    }
    testImplementation("org.testcontainers:postgresql:1.21.3")
    constraints {
        implementation("org.apache.commons:commons-compress:1.28.0") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
    }
    testImplementation("org.assertj:assertj-core:3.27.4")

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
