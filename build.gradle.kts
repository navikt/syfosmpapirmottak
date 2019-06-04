import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.27-SNAPSHOT"

val avroVersion = "1.8.2"
val coroutinesVersion = "1.1.1"
val fellesformatVersion = "1.0"
val kafkaVersion = "2.0.0"
val kafkaEmbeddedVersion = "2.1.1"
val kithHodemeldingVersion = "1.1"
val kluentVersion = "1.39"
val ktorVersion = "1.2.1"
val logbackVersion = "1.2.3"
val logstashLogbackEncoderVersion = "5.2"
val prometheusVersion = "0.5.0"
val spekVersion = "2.0.2"
val jaxbApiVersion = "2.1"
val jaxbVersion = "2.3.0.1"
val javaxActivationVersion = "1.1.1"
val papirSykemeldingVersion = "1.0.0-SNAPSHOT"
val jacksonVersion = "2.9.6"
val joarkHendelseVersion = "0.0.3"
val confluentVersion = "5.0.2"
val jettyVersion = "9.4.11.v20180605"
val sykmelding2013Version = "1.1-SNAPSHOT"
val junitPlatformLauncher = "1.0.0"
val syfooppgaveSchemasVersion = "1.2-SNAPSHOT"
val navPersonv3Version = "3.2.0"
val navArbeidsfordelingv1Version = "1.1.0"
val commonsTextVersion = "1.4"
val cxfVersion = "3.2.7"
val jaxwsApiVersion = "2.3.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val jaxwsToolsVersion = "2.3.1"
val smCommonVersion = "1.0.20"


plugins {
    java
    kotlin("jvm") version "1.3.31"
    id("org.jmailen.kotlinter") version "1.26.0"
    id("com.diffplug.gradle.spotless") version "3.18.0"
    id("com.github.johnrengelman.shadow") version "4.0.4"
}

repositories {
    mavenCentral()
    jcenter()
    maven (url= "https://kotlin.bintray.com/kotlinx")
    maven (url= "https://dl.bintray.com/kotlin/ktor")
    maven (url= "https://dl.bintray.com/spekframework/spek-dev")
    maven (url= "https://repo.adeo.no/repository/maven-snapshots/")
    maven (url= "https://repo.adeo.no/repository/maven-releases/")
    maven (url= "http://packages.confluent.io/maven/")
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
    // Override avro version cause its not 1.8.1 is not compatible with team dokuments generated beans
    implementation("org.apache.avro:avro:$avroVersion")
    implementation ("no.nav.dok:dok-journalfoering-hendelse-v1:$joarkHendelseVersion")

    implementation ("no.nav.syfo.tjenester:fellesformat:$fellesformatVersion")
    implementation ("no.nav.syfo.tjenester:kith-hodemelding:$kithHodemeldingVersion")
    implementation ("no.nav.helse.xml:papirSykemelding:$papirSykemeldingVersion")
    implementation ("no.nav.helse.xml:sm2013:$sykmelding2013Version")
    implementation ("no.nav.syfo:syfooppgave-schemas:$syfooppgaveSchemasVersion")
    implementation ("no.nav.tjenester:nav-person-v3-tjenestespesifikasjon:$navPersonv3Version")
    implementation ("no.nav.tjenester:nav-arbeidsfordeling-v1-tjenestespesifikasjon:$navArbeidsfordelingv1Version:jaxws")

    implementation ("io.ktor:ktor-client-cio:$ktorVersion")
    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation ("io.ktor:ktor-client-auth-basic-jvm:$ktorVersion")
    implementation ("io.ktor:ktor-client-gson:$ktorVersion")
    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation ("io.ktor:ktor-client-logging:$ktorVersion")
    implementation ("io.ktor:ktor-client-logging-jvm:$ktorVersion")

    implementation ("no.nav.syfo.sm:syfosm-common-models:$smCommonVersion")
    implementation ("no.nav.syfo.sm:syfosm-common-networking:$smCommonVersion")
    implementation ("no.nav.syfo.sm:syfosm-common-rest-sts:$smCommonVersion")
    implementation ("no.nav.syfo.sm:syfosm-common-ws:$smCommonVersion")
    implementation ("no.nav.syfo.sm:syfosm-common-kafka:$smCommonVersion")

    implementation ("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation ("org.glassfish.jaxb:jaxb-runtime:$jaxbVersion")
    implementation ("javax.activation:activation:$javaxActivationVersion")

    implementation ("org.apache.commons:commons-text:$commonsTextVersion")
    implementation ("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation ("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation ("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation ("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")

    implementation ("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    implementation ("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation ("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation ("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation ("javax.activation:activation:$javaxActivationVersion")
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }

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
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
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
