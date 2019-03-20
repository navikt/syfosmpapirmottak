package no.nav.syfo

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.util.loadBaseConfig
import no.nav.syfo.util.toConsumerConfig
import no.nav.syfo.util.toProducerConfig
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
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
            topicNames = listOf(topic),
            withSchemaRegistry = true,
            withSecurity = true,
            users = listOf(JAASCredential(credentials.serviceuserUsername, credentials.serviceuserPassword))
            )

    val config = ApplicationConfig(
            applicationPort = getRandomPort(),
            kafkaBootstrapServers = embeddedEnvironment.brokersURL,
            applicationThreads = 1,
            kafkaSM2013PapirmottakTopic = "",
            syfoSmRegelerApiURL = "",
            kafkaSM2013OppgaveGsakTopic = "",
            dokJournalfoeringV1 = "",
            journalfoerInngaaendeV1URL = "",
            safURL = "",
            applicationName = "syfosmpapirmottak"
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }

    val baseConfig = loadBaseConfig(config, credentials).overrideForTest()

    val producerProperties = baseConfig
            .toProducerConfig("spek.integration", valueSerializer = KafkaAvroSerializer::class)
    val producer = KafkaProducer<String, JournalfoeringHendelseRecord>(producerProperties)

    val consumerProperties = baseConfig
            .toConsumerConfig("spek.integration-consumer", valueDeserializer = StringDeserializer::class)
    val consumer = KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerProperties)

    consumer.subscribe(listOf(topic))

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterGroup {
        embeddedEnvironment.tearDown()
    }

    describe("Push a message on a topic") {
        val message = JournalfoeringHendelseRecord()

        it("Can read the messages from the kafka topic") {
            producer.send(ProducerRecord(topic, message))

            val messages = consumer.poll(Duration.ofMillis(10000)).toList()
            messages.size shouldEqual 1
            messages[0].value() shouldEqual message
        }
    }
})
