import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val apolloVersion = "1.2.2"
val coroutinesVersion = "1.3.4"
val fellesformatVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val kafkaVersion = "2.3.0"
val kafkaEmbeddedVersion = "2.4.0"
val kithHodemeldingVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val kluentVersion = "1.39"
val ktorVersion = "1.3.2"
val logbackVersion = "1.2.3"
val logstashLogbackEncoderVersion = "5.2"
val prometheusVersion = "0.5.0"
val spekVersion = "2.0.9"
val jaxbApiVersion = "2.1"
val jaxbVersion = "2.3.0.1"
val javaxActivationVersion = "1.1.1"
val papirSykemeldingVersion = "2019.09.09-08-50-693492ddc1d3f98e70c1638c94dcb95a66036d12"
val jacksonVersion = "2.9.6"
val joarkHendelseVersion = "67a9be4476b63b7247cfacfaf821ab656bd2a952"
val confluentVersion = "5.0.2"
val jettyVersion = "9.4.11.v20180605"
val sykmelding2013Version = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val syfooppgaveSchemasVersion = "c8be932543e7356a34690ce7979d494c5d8516d8"
val navPersonv3Version = "1.2019.07.11-06.47-b55f47790a9d"
val diskresjonskodeV1Version = "1.2019.07.11-06.47-b55f47790a9d"
val navArbeidsfordelingv1Version = "1.2019.07.11-06.47-b55f47790a9d"
val commonsTextVersion = "1.4"
val cxfVersion = "3.2.7"
val jaxwsApiVersion = "2.3.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val jaxwsToolsVersion = "2.3.1"
val smCommonVersion = "1.125b076"
val javaxJaxwsApiVersion = "2.2.1"
val javaTimeAdapterVersion = "1.1.3"
val ioMockVersion = "1.9.3"
val smCommonDiagnosisCodesVersion = "1.68817ee"


plugins {
    java
    id("no.nils.wsdl2java") version "0.10"
    kotlin("jvm") version "1.3.71"
    id("com.diffplug.gradle.spotless") version "3.18.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("com.apollographql.android") version "1.2.2"
    id("org.jmailen.kotlinter") version "2.2.0"
}

buildscript {
    repositories {
        jcenter()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    dependencies {
        classpath("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
        classpath("org.glassfish.jaxb:jaxb-runtime:2.4.0-b180830.0438")
        classpath("com.sun.activation:javax.activation:1.2.0")
        classpath("com.sun.xml.ws:jaxws-tools:2.3.1") {
            exclude(group = "com.sun.xml.ws", module = "policy")
        }
        classpath("com.apollographql.apollo:apollo-gradle-plugin:1.2.2")
    }
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    jcenter()
    maven (url= "https://kotlin.bintray.com/kotlinx")
    maven (url= "https://dl.bintray.com/kotlin/ktor")
    maven (url= "https://dl.bintray.com/spekframework/spek-dev")
    maven (url= "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
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
    implementation ("no.nav.syfo.schemas:dok-journalfoering-hendelse-v1:$joarkHendelseVersion")

    implementation ("no.nav.helse.xml:xmlfellesformat:$fellesformatVersion")
    implementation ("no.nav.helse.xml:kith-hodemelding:$kithHodemeldingVersion")
    implementation ("no.nav.helse.xml:papirSykemelding:$papirSykemeldingVersion")
    implementation ("no.nav.helse.xml:sm2013:$sykmelding2013Version")
    implementation ("no.nav.syfo.schemas:syfosmoppgave-avro:$syfooppgaveSchemasVersion")

    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation ("io.ktor:ktor-client-auth-basic-jvm:$ktorVersion")
    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation ("io.ktor:ktor-jackson:$ktorVersion")

    implementation ("com.apollographql.apollo:apollo-runtime:$apolloVersion")

    implementation ("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-networking:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-rest-sts:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-ws:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-diagnosis-codes:$smCommonDiagnosisCodesVersion")
    implementation ("no.nav.helse:syfosm-common-mq:$smCommonVersion")

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

    implementation("com.migesok", "jaxb-java-time-adapters", javaTimeAdapterVersion)
    implementation("no.nav.tjenestespesifikasjoner:person-v3-tjenestespesifikasjon:$navPersonv3Version")
    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation ("org.eclipse.jetty:jetty-servlet:$jettyVersion")
    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation ("io.mockk:mockk:$ioMockVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
    }
    testImplementation ("io.ktor:ktor-jackson:$ktorVersion")
    testImplementation ("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")

    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
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
        kotlinOptions.jvmTarget = "12"
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


    "check" {
        dependsOn("formatKotlin")
    }
}
