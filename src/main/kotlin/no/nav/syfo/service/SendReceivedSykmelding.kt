package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

fun sendReceivedSykmeldingToKafka(
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    okSykmeldingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta
) {
    try {
        kafkaproducerreceivedSykmelding
            .send(
                ProducerRecord(
                    okSykmeldingTopic,
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding
                )
            )
            .get()
        log.info(
            "Message send to kafka {}, {}",
            okSykmeldingTopic,
            StructuredArguments.fields(loggingMeta)
        )
    } catch (ex: Exception) {
        log.error(
            "Failed to send ReceivedSykmelding to kafka {}",
            StructuredArguments.fields(loggingMeta)
        )
        throw ex
    }
}
