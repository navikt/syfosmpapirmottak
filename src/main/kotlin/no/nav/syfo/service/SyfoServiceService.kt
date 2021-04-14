package no.nav.syfo.service

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.domain.KafkaMessageMetadata
import no.nav.syfo.domain.SyfoserviceSykmeldingKafkaMessage
import no.nav.syfo.domain.Tilleggsdata
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractSyketilfelleStartDato
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

fun notifySyfoService(
    syfoserviceProducer: KafkaProducer<String, SyfoserviceSykmeldingKafkaMessage>,
    syfoserviceTopic: String,
    ediLoggId: String,
    sykmeldingId: String,
    msgId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    loggingMeta: LoggingMeta
) {

    val syketilfelleStartDato = extractSyketilfelleStartDato(healthInformation)

    val syfo = SyfoserviceSykmeldingKafkaMessage(
        helseopplysninger = healthInformation,
        tilleggsdata = Tilleggsdata(ediLoggId = ediLoggId, sykmeldingId = sykmeldingId, msgId = msgId, syketilfelleStartDato = syketilfelleStartDato),
        metadata = KafkaMessageMetadata(sykmeldingId, "syfosmpapirmottak")
    )

    try {
        syfoserviceProducer.send(ProducerRecord(syfoserviceTopic, sykmeldingId, syfo)).get()
        log.info("Sendt sykmelding til syfoservice-mq-producer {} {}", sykmeldingId, loggingMeta)
    } catch (ex: Exception) {
        log.error("Could not send sykemelding to syfoservice kafka {} {}", sykmeldingId, loggingMeta)
        throw ex
    }
}
