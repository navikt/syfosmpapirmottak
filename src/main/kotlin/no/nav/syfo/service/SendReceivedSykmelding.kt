package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

const val PROCESSING_TARGET_HEADER = "processing-target"
const val TSM_PROCESSING_TARGET_VALUE = "tsm"

fun sendReceivedSykmeldingToKafka(
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmeldingWithValidation>,
    okSykmeldingTopic: String,
    receivedSykmelding: ReceivedSykmeldingWithValidation,
    loggingMeta: LoggingMeta
) {
    try {
        val record =
            ProducerRecord(okSykmeldingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding)
        record.headers().add(PROCESSING_TARGET_HEADER, TSM_PROCESSING_TARGET_VALUE.toByteArray())
        kafkaproducerreceivedSykmelding.send(record).get()
        log.info(
            "Message send to kafka $okSykmeldingTopic, ${StructuredArguments.fields(loggingMeta)}",
        )
    } catch (ex: Exception) {
        log.error(
            "Failed to send ReceivedSykmelding to kafka {}",
            StructuredArguments.fields(loggingMeta)
        )
        throw ex
    }
}
