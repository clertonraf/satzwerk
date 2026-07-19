plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.satzwerk"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

fun resolveDockerHost(): String? {
    val configuredHost = System.getenv("DOCKER_HOST")
    if (!configuredHost.isNullOrBlank()) {
        return configuredHost
    }

    return runCatching {
        val contextName =
            providers.exec {
                commandLine("docker", "context", "show")
            }.standardOutput.asText.get().trim()

        if (contextName.isBlank()) {
            null
        } else {
            providers.exec {
                commandLine("docker", "context", "inspect", contextName, "--format", "{{ .Endpoints.docker.Host }}")
            }
                .standardOutput.asText.get().trim()
                .ifBlank { null }
        }
    }.getOrNull()
}

val dockerHost = resolveDockerHost()

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.postgresql:r2dbc-postgresql")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("detekt.yml"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxParallelForks = 1

    if (!dockerHost.isNullOrBlank()) {
        environment("DOCKER_HOST", dockerHost)

        if (dockerHost.startsWith("unix://")) {
            environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
        }
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
}
