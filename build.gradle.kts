import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.11.0"
val kafkaVersion = "4.3.1"
val kluentVersion = "1.73"
val ktorVersion = "3.6.0"
val logbackVersion = "1.6.5"
val logstashLogbackEncoderVersion = "9.0"
val prometheusVersion = "0.16.0"
val kotestVersion = "6.2.5"
val jaxbApiVersion = "2.1"
val jaxbVersion = "2.3.0.1"
val javaxActivationVersion = "1.1.1"
val jacksonVersion = "3.2.3"
val joarkHendelseVersion = "1.1.6"
val confluentVersion = "8.1.4"
val syfoXmlCodegenVersion = "2.0.1"
val commonsTextVersion = "1.15.0"
val javaxAnnotationApiVersion = "1.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val javaTimeAdapterVersion = "1.1.3"
val ioMockVersion = "1.14.11"
val kotlinVersion = "2.4.20"
val caffeineVersion = "3.3.0"
val ktfmtVersion = "0.56"
val diagnosekoderVersion = "1.2026.0"
val googleCloudStorageVersion = "2.75.0"

val javaVersion = JvmTarget.JVM_25

plugins {
    id("application")
    kotlin("jvm") version "2.4.20"
    id("com.diffplug.spotless") version "8.10.3"
    id("io.github.ben-manes.versions") version "0.64.0"
}

application {
    mainClass.set("no.nav.syfo.BootstrapKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}


repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}


dependencies {
    implementation("tools.jackson.module:jackson-module-jaxb-annotations:${jacksonVersion}")
    implementation("tools.jackson.module:jackson-module-kotlin:${jacksonVersion}")
    implementation("tools.jackson.dataformat:jackson-dataformat-xml:${jacksonVersion}")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("no.nav.teamdokumenthandtering:teamdokumenthandtering-avro-schemas:$joarkHendelseVersion")

    implementation("no.nav.helse.xml:xmlfellesformat:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:kith-hodemelding:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:papirsykemelding:$syfoXmlCodegenVersion")
    implementation("no.nav.helse.xml:sm2013:$syfoXmlCodegenVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson3:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")

    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")

    implementation("no.nav.helse:diagnosekoder:$diagnosekoderVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")
    implementation("org.apache.commons:commons-text:$commonsTextVersion")
    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")


    implementation("com.migesok:jaxb-java-time-adapters:$javaTimeAdapterVersion")

    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.mockk:mockk:$ioMockVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
}


kotlin {
    compilerOptions {
        jvmTarget = javaVersion
    }
}


tasks {

   test {
        useJUnitPlatform {
        }
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }


    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }

    named<DependencyUpdatesTask>("dependencyUpdates") {
        fun String.isNonStable(): Boolean {
            val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { uppercase().contains(it) }
            val regex = "^[0-9,.v-]+(-r)?$".toRegex()
            val isStable = stableKeyword || regex.matches(this)
            return isStable.not()
        }

        rejectVersionIf {
            candidate.version.isNonStable()
        }
    }
}
