package no.nav.syfo

import java.net.ServerSocket
import java.time.Duration
import java.util.Properties
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KafkaITSpek : Spek({
    val topic = "aapen-test-topic"
    fun getRandomPort() = ServerSocket(0).use {
        it.localPort
    }

    val credentials = VaultCredentials(
            serviceuserUsername = " ",
            serviceuserPassword = "",
            clientsecret = "secret",
            mqPassword = "",
            mqUsername = ""
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
            clientId = "clientId",
            helsenettproxyId = "helsenettproxyId",
            aadAccessTokenUrl = "azuread/token",
            papirregelId = "papirregelId",
            mqChannelName = "mqchannel",
            mqGatewayName = "mqGateway",
            mqHostname = "mqcl01",
            mqPort = 1413,
            syfoserviceQueueName = "syfoservicekø",
            dokArkivUrl = "dokapi/",
            cluster = "dev",
            pdlGraphqlPath = "TEST",
            truststore = "truststore",
            truststorePassword = "pwd"
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
            messages.size shouldEqual 1
            messages[0].value() shouldEqual message
        }
    }
})
