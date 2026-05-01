plugins {
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.graalvm.buildtools.native") version "1.1.0"
	id("org.hibernate.orm") version "7.3.2.Final"
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	kotlin("plugin.jpa") version "2.3.21"
}

group = "no.nav.reops"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(
			providers.gradleProperty("javaVersion").getOrElse("25")
		)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("org.postgresql:postgresql")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
	testImplementation("org.junit.platform:junit-platform-launcher")
	testImplementation("org.assertj:assertj-core")
	testImplementation("io.zonky.test:embedded-postgres:2.2.2")
	testImplementation("io.zonky.test:embedded-database-spring-test:2.8.0")
	testImplementation("org.postgresql:postgresql")
	testImplementation("org.flywaydb.flyway-test-extensions:flyway-spring-test:10.0.0")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

hibernate {
	enhancement {
		enableLazyInitialization = true
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<Jar>("bootJar") {
	archiveFileName.set("app.jar")
}

graalvmNative {
	binaries {
		named("main") {
			imageName.set("app")
			mainClass.set("no.nav.reops.InnblikkBackendApplicationKt")
		}
	}
	binaries.all {
		buildArgs.addAll(
			"-H:+ReportExceptionStackTraces",

			// Strip debug symbols from the binary
			"-H:-IncludeMethodData",

			// Optimize for size over peak throughput
			"-Os",
		)

		if (System.getenv("CI").toBoolean()) {
			buildArgs.add("-J-Xmx32g")
		} else {
			buildArgs.add("-J-Xmx6g")
		}
	}
	// Zonky embedded-database-spring-test is incompatible with Spring AOT code generation
	testSupport.set(false)
}

tasks.named("processTestAot") {
	enabled = false
}

tasks.register<Exec>("generateChangelog") {
	group = "documentation"
	description = "Generates CHANGELOG.md from Conventional Commits using git-cliff (no versioning)."
	commandLine(
		"bash", "-lc",
		"""
        set -euo pipefail
        git cliff --config cliff.toml --output CHANGELOG.md
        """.trimIndent()
	)
}
