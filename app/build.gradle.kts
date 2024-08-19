plugins {
    kotlin("jvm") version "1.9.23"
    id("io.ktor.plugin") version "2.3.9"
    application
}

val aapLibVersion = "5.0.23"
val ktorVersion = "2.3.12"

application {
    mainClass.set("innsending.AppKt")
}

dependencies {
    implementation("com.github.navikt.aap-libs:ktor-auth:$aapLibVersion")
    implementation("com.github.navikt.aap-libs:kafka:$aapLibVersion")
    implementation("org.apache.kafka:kafka-clients:3.8.0")

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
    implementation("org.apache.tika:tika-core:2.9.1")

    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("redis.clients:jedis:5.1.4")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.2")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("org.apache.pdfbox:pdfbox:3.0.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:10.17.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")
    runtimeOnly("org.postgresql:postgresql:42.7.3")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("org.assertj:assertj-core:3.26.0")

}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
    withType<Test> {
        useJUnitPlatform()
    }
    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        mergeServiceFiles()
    }
}

kotlin.sourceSets["main"].kotlin.srcDirs("main")
kotlin.sourceSets["test"].kotlin.srcDirs("test")
sourceSets["main"].resources.srcDirs("main")
sourceSets["test"].resources.srcDirs("test")
