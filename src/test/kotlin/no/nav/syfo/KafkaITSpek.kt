package no.nav.syfo

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.util.readConsumerConfig
import no.nav.syfo.util.readProducerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.time.Duration

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
            topics = listOf(topic),
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
            journalfoerInngaaendeV1URL = ""
    )

    val producer = KafkaProducer<String, JournalfoeringHendelseRecord>(readProducerConfig(config, credentials, KafkaAvroSerializer::class).apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    })

    val consumer = KafkaConsumer<String, JournalfoeringHendelseRecord>(readConsumerConfig(config, credentials).apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    })
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
            // TODO messages.size shouldEqual 1
            // TODO messages[0].value() shouldEqual message
        }
    }
})
