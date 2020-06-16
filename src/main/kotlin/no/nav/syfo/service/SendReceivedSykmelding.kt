package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

fun sendReceivedSykmeldingToKafka(kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>, sm2013Topic: String, receivedSykmelding: ReceivedSykmelding, loggingMeta: LoggingMeta) {
    try {
        kafkaproducerreceivedSykmelding.send(ProducerRecord(sm2013Topic, receivedSykmelding.sykmelding.id, receivedSykmelding)).get()
        log.info("Message send to kafka {}, {}", sm2013Topic, StructuredArguments.fields(loggingMeta))
    } catch (ex: Exception) {
        log.error("Failed to send ReceivedSykmelding to kafka {}", StructuredArguments.fields(loggingMeta))
        throw ex
    }
}
