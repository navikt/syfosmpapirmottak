import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val apolloVersion = "2.5.14"
val coroutinesVersion = "1.7.1"
val kafkaVersion = "3.4.0"
val kluentVersion = "1.73"
val ktorVersion = "2.3.0"
val logbackVersion = "1.4.7"
val logstashLogbackEncoderVersion = "7.3"
val prometheusVersion = "0.16.0"
val kotestVersion = "5.6.2"
val jaxbApiVersion = "2.1"
val jaxbVersion = "2.3.0.1"
val javaxActivationVersion = "1.1.1"
val jacksonVersion = "2.15.1"
val joarkHendelseVersion = "96ec5ebb"
val confluentVersion = "7.2.1"
val jettyVersion = "11.0.6"
val syfoXmlCodegenVersion = "1.0.4"
val commonsTextVersion = "1.10.0"
val cxfVersion = "3.4.5"
val javaxAnnotationApiVersion = "1.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val smCommonVersion = "1.0.1"
val javaTimeAdapterVersion = "1.1.3"
val ioMockVersion = "1.13.5"
val kotlinVersion = "1.8.21"
val okhttp3Version = "4.11.0"
val commonsCodecVersion = "1.15"
val caffeineVersion = "3.1.6"


plugins {
    java
    kotlin("jvm") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.apollographql.apollo") version "2.5.14"
    id("org.jmailen.kotlinter") version "3.14.0"
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
        classpath("com.apollographql.apollo:apollo-gradle-plugin:2.5.14")
    }
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    google()
    maven (url= "https://packages.confluent.io/maven/")
    maven {
        name = "syfosm-common"
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
    maven {
        name = "syfo-xml-codegen"
        url = uri("https://maven.pkg.github.com/navikt/syfo-xml-codegen")
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
    implementation ("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation ("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation ("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation ("ch.qos.logback:logback-classic:$logbackVersion")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")

    implementation ("org.apache.kafka:kafka_2.12:$kafkaVersion")
    implementation ("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation ("no.nav.teamdokumenthandtering:teamdokumenthandtering-avro-schemas:$joarkHendelseVersion")

    implementation ("no.nav.helse.xml:xmlfellesformat:$syfoXmlCodegenVersion")
    implementation ("no.nav.helse.xml:kith-hodemelding:$syfoXmlCodegenVersion")
    implementation ("no.nav.helse.xml:papirsykemelding:$syfoXmlCodegenVersion")
    implementation ("no.nav.helse.xml:sm2013:$syfoXmlCodegenVersion")

    implementation ("io.ktor:ktor-server-core:$ktorVersion")
    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation ("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation ("io.ktor:ktor-client-core:$ktorVersion")
    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("commons-codec:commons-codec:$commonsCodecVersion")// override transient version 1.10

    implementation ("com.apollographql.apollo:apollo-runtime:$apolloVersion") {
        exclude("com.squareup.okhttp3")
    }
    implementation ("com.squareup.okhttp3:okhttp:$okhttp3Version")

    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")


    implementation ("no.nav.helse:syfosm-common-models:$smCommonVersion")
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
    testImplementation ("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation ("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation ("io.mockk:mockk:$ioMockVersion")
    testImplementation ("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
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
        dependsOn("generateTestServiceApolloSources")
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
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }


    "check" {
        dependsOn("formatKotlin")
    }
}
