package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

fun sendPapirSmRegistreringToKafka(kafkaproducerPapirSmRegistering: KafkaProducer<String, PapirSmRegistering>, sm2013SmregistreringTopic: String, papirSmRegistering: PapirSmRegistering, loggingMeta: LoggingMeta) {
    try {
        kafkaproducerPapirSmRegistering.send(ProducerRecord(sm2013SmregistreringTopic, papirSmRegistering.sykmeldingId, papirSmRegistering)).get()
        log.info("Message send to kafka {}, {}", sm2013SmregistreringTopic, StructuredArguments.fields(loggingMeta))
    } catch (ex: Exception) {
        log.error("Failed to send PapirSmRegistrering to kafka {}", StructuredArguments.fields(loggingMeta))
        throw ex
    }
}
