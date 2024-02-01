plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.7"
    application
}

val aapLibVersion = "3.7.146"
val ktorVersion = "2.3.8"

application {
    mainClass.set("innsending.AppKt")
}

dependencies {
    implementation("com.github.navikt.aap-libs:ktor-auth-azuread:$aapLibVersion")

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
    implementation("redis.clients:jedis:5.1.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.2")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    implementation("org.apache.pdfbox:pdfbox:3.0.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:10.7.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.6.0")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.4")
    runtimeOnly("org.postgresql:postgresql:42.7.1")

//    implementation("io.opentelemetry:opentelemetry-sdk:1.34.1")
//    implementation("io.opentelemetry:opentelemetry-exporter-logging:1.34.1")
    //implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.34.1")

    //implementation("io.opentelemetry:opentelemetry-bom:1.34.1")
    
    // Manually instrumentation
    implementation("io.opentelemetry:opentelemetry-api:1.34.1")

    // Instrument application
    implementation("io.opentelemetry:opentelemetry-sdk:1.34.1");
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.34.1");
    implementation("io.opentelemetry:opentelemetry-exporter-logging:1.34.1");
//    implementation("io.opentelemetry:opentelemetry-semconv:1.34.1-alpha");

    // Instrument ktor
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-2.0:2.0.0-alpha")
    //implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.0.0-alpha")


    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
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
