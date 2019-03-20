import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

group = "no.nav.syfo"
version = "1.0.27"

val coroutinesVersion = "1.0.1"
val fellesformatVersion = "1.0"
val kafkaVersion = "2.1.1"
val kafkaEmbeddedVersion = "2.1.1"
val kithHodemeldingVersion = "1.1"
val kluentVersion = "1.39"
val ktorVersion = "1.1.3"
val logbackVersion = "1.2.3"
val logstashLogbackEncoderVersion = "5.2"
val prometheusVersion = "0.5.0"
val spekVersion = "2.0.0"
val jaxbApiVersion = "2.1"
val jaxbVersion = "2.3.0.1"
val javaxActivationVersion = "1.1.1"
val papirSykemeldingVersion = "1.0.0-SNAPSHOT"
val jacksonVersion = "2.9.6"
val joarkHendelseVersion = "0.0.3"
val confluentVersion = "5.0.0"
val jettyVersion = "9.4.11.v20180605"
val sykmelding2013Version = "1.1-SNAPSHOT"
val junitPlatformLauncher = "1.0.0"

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
}


plugins {
    java
    kotlin("jvm") version "1.3.21"
    id("org.jmailen.kotlinter") version "1.21.0"
    id("com.diffplug.gradle.spotless") version "3.18.0"
    id("com.github.johnrengelman.shadow") version "4.0.4"
}


repositories {
    maven (url= "https://repo.adeo.no/repository/maven-snapshots/")
    maven (url= "https://repo.adeo.no/repository/maven-releases/")
    maven (url= "https://dl.bintray.com/kotlin/ktor")
    maven (url= "https://dl.bintray.com/spekframework/spek-dev")
    maven (url= "http://packages.confluent.io/maven/")
    maven (url= "https://kotlin.bintray.com/kotlinx")
    mavenCentral()
    jcenter()
}


dependencies {
    implementation(kotlin("stdlib"))

    implementation ("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation ("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation ("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation ("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation ("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation ("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation ("ch.qos.logback:logback-classic:$logbackVersion")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")

    implementation ("org.apache.kafka:kafka_2.12:$kafkaVersion")
    implementation ("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation ("no.nav.dok:dok-journalfoering-hendelse-v1:$joarkHendelseVersion")

    implementation ("no.nav.syfo.tjenester:fellesformat:$fellesformatVersion")
    implementation ("no.nav.syfo.tjenester:kith-hodemelding:$kithHodemeldingVersion")
    implementation ("no.nav.helse.xml:papirSykemelding:$papirSykemeldingVersion")
    implementation ("no.nav.helse.xml:sm2013:$sykmelding2013Version")

    implementation ("io.ktor:ktor-client-cio:$ktorVersion")
    implementation ("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation ("io.ktor:ktor-client-gson:$ktorVersion")
    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation ("io.ktor:ktor-client-logging:$ktorVersion")
    implementation ("io.ktor:ktor-client-logging-jvm:$ktorVersion")

    implementation ("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation ("org.glassfish.jaxb:jaxb-runtime:$jaxbVersion")
    implementation ("javax.activation:activation:$javaxActivationVersion")

    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation ("org.eclipse.jetty:jetty-servlet:$jettyVersion")
    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
    }
    testImplementation("org.junit.platform:junit-platform-launcher:$junitPlatformLauncher")
    testImplementation ("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")

    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:$spekVersion")
    {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    {
        exclude(group = "org.jetbrains.kotlin")
    }

}


tasks {
    create("printVersion") {
        println(project.version)
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
