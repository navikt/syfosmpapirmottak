package no.nav.syfo

import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.time.Duration
import java.util.Properties

object KafkaITSpek : Spek({
    val topic = "aapen-test-topic"
    fun getRandomPort() = ServerSocket(0).use {
        it.localPort
    }

    val credentials = VaultCredentials(
        serviceuserUsername = " ",
        serviceuserPassword = ""
    )

    val embeddedEnvironment = KafkaEnvironment(
        autoStart = false,
        topicNames = listOf(topic)
    )

    val config = Environment(
        applicationPort = getRandomPort(),
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        safV1Url = "saf/api",
        applicationName = "syfosmpapirmottak",
        dokJournalfoeringV1Topic = "aapen-dok-journalfoering-v1-q1",
        securityTokenServiceUrl = "securl",
        oppgavebehandlingUrl = "oppgave/api",
        hentDokumentUrl = "saf",
        helsenettproxyScope = "helsenettproxyScope",
        syfosmpapirregelScope = "papirregelScope",
        dokArkivUrl = "dokapi/",
        cluster = "dev",
        pdlGraphqlPath = "TEST",
        truststore = "truststore",
        truststorePassword = "pwd",
        norskHelsenettEndpointURL = "url",
        regelEndpointURL = "regelurl",
        aadAccessTokenV2Url = "aadAccessTokenV2Url",
        clientIdV2 = "clientIdV2",
        clientSecretV2 = "clientSecretV2",
        pdlScope = "pdlScope",
        opprettSakUrl = "sak"
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }

    val baseConfig = loadBaseConfig(config, credentials).overrideForTest()

    val producer = KafkaProducer<String, String>(baseConfig.toProducerConfig("spek-test", StringSerializer::class))
    val consumer = KafkaConsumer<String, String>(baseConfig.toConsumerConfig("spek-test", StringDeserializer::class))

    consumer.subscribe(listOf(topic))

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterGroup {
        embeddedEnvironment.tearDown()
    }

    describe("Push a message on a topic") {
        val message = "Test message"
        it("Can read the messages from the kafka topic") {
            producer.send(ProducerRecord(topic, message))

            val messages = consumer.poll(Duration.ofMillis(10000)).toList()
            messages.size shouldBeEqualTo 1
            messages[0].value() shouldBeEqualTo message
        }
    }
})
