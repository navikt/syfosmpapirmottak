import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val apolloVersion = "2.5.11"
val coroutinesVersion = "1.6.1"
val fellesformatVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val kafkaVersion = "3.1.0"
val kithHodemeldingVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val kluentVersion = "1.68"
val ktorVersion = "1.6.8"
val logbackVersion = "1.2.11"
val logstashLogbackEncoderVersion = "7.1.1"
val prometheusVersion = "0.15.0"
val kotestVersion = "5.2.3"
val jaxbApiVersion = "2.1"
val jaxbVersion = "2.3.0.1"
val javaxActivationVersion = "1.1.1"
val papirSykemeldingVersion = "2019.09.09-08-50-693492ddc1d3f98e70c1638c94dcb95a66036d12"
val jacksonVersion = "2.13.2"
val jacksonPatchVersion = "2.13.2.2"
val jacksonBomVersion = "2.13.2.20220328"
val joarkHendelseVersion = "96ec5ebb"
val confluentVersion = "6.2.2"
val jettyVersion = "11.0.6"
val sykmelding2013Version = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val commonsTextVersion = "1.9"
val cxfVersion = "3.4.5"
val javaxAnnotationApiVersion = "1.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val smCommonVersion = "1.ed38c78"
val javaTimeAdapterVersion = "1.1.3"
val ioMockVersion = "1.12.3"
val kotlinVersion = "1.6.20"

plugins {
    java
    kotlin("jvm") version "1.6.20"
    id("com.diffplug.spotless") version "6.5.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.apollographql.apollo") version "2.5.11"
    id("org.jmailen.kotlinter") version "3.10.0"
}

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    dependencies {
        classpath("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
        classpath("org.glassfish.jaxb:jaxb-runtime:2.4.0-b180830.0438")
        classpath("com.sun.activation:javax.activation:1.2.0")
        classpath("com.apollographql.apollo:apollo-gradle-plugin:2.5.11")
    }
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    google()
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
    implementation ("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation ("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation ("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation ("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation ("com.fasterxml.jackson:jackson-bom:$jacksonBomVersion")
    implementation ("com.fasterxml.jackson.core:jackson-databind:$jacksonPatchVersion")

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation ("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation ("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation ("ch.qos.logback:logback-classic:$logbackVersion")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")

    implementation ("org.apache.kafka:kafka_2.12:$kafkaVersion")
    implementation ("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation ("no.nav.teamdokumenthandtering:teamdokumenthandtering-avro-schemas:$joarkHendelseVersion")

    implementation ("no.nav.helse.xml:xmlfellesformat:$fellesformatVersion")
    implementation ("no.nav.helse.xml:kith-hodemelding:$kithHodemeldingVersion")
    implementation ("no.nav.helse.xml:papirSykemelding:$papirSykemeldingVersion")
    implementation ("no.nav.helse.xml:sm2013:$sykmelding2013Version")

    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation ("io.ktor:ktor-client-auth-basic-jvm:$ktorVersion")
    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation ("io.ktor:ktor-jackson:$ktorVersion")

    implementation ("com.apollographql.apollo:apollo-runtime:$apolloVersion")

    implementation ("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-networking:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-rest-sts:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-diagnosis-codes:$smCommonVersion")

    implementation ("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation ("org.glassfish.jaxb:jaxb-runtime:$jaxbVersion")
    implementation ("javax.activation:activation:$javaxActivationVersion")

    implementation ("org.apache.commons:commons-text:$commonsTextVersion")

    implementation ("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation ("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation ("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation ("javax.activation:activation:$javaxActivationVersion")
    implementation ("com.migesok", "jaxb-java-time-adapters", javaTimeAdapterVersion)

    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation ("io.mockk:mockk:$ioMockVersion")
    testImplementation ("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
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
        kotlinOptions.jvmTarget = "17"
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
        }
        testLogging.showStandardStreams = true
    }


    "check" {
        dependsOn("formatKotlin")
    }
}
