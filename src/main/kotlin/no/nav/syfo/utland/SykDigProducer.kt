package no.nav.syfo.utland

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykDigProducer(
    private val kafkaProducer: KafkaProducer<String, DigitaliseringsoppgaveKafka>,
    private val topicName: String
) {
    fun send(sykmeldingId: String, digitaliseringsoppgave: DigitaliseringsoppgaveKafka, loggingMeta: LoggingMeta) {
        try {
            kafkaProducer.send(
                ProducerRecord(
                    topicName,
                    sykmeldingId,
                    digitaliseringsoppgave
                )
            ).get()
        } catch (ex: Exception) {
            log.error(
                "Noe gikk galt ved skriving av digitaliseringsoppgave til kafka for oppgaveId ${digitaliseringsoppgave.oppgaveId} og sykmeldingId $sykmeldingId, {}",
                StructuredArguments.fields(loggingMeta),
                ex.message
            )
            throw ex
        }
    }
}
