package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.domain.SyfoserviceSykmeldingKafkaMessage
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer

suspend fun handleOk(
    kafkaReceivedSykmeldingProducer: KafkaProducer<String, ReceivedSykmelding>,
    okSykmeldingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    syfoserviceProducer: KafkaProducer<String, SyfoserviceSykmeldingKafkaMessage>,
    syfoserviceTopic: String,
    sykmeldingId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    dokArkivClient: DokArkivClient,
    journalpostid: String,
    loggingMeta: LoggingMeta
) {

    dokArkivClient.oppdaterOgFerdigstillJournalpost(journalpostId = journalpostid, fnr = receivedSykmelding.personNrPasient, sykmeldingId = sykmeldingId, behandler = receivedSykmelding.sykmelding.behandler, loggingMeta = loggingMeta)

    sendReceivedSykmeldingToKafka(kafkaReceivedSykmeldingProducer, okSykmeldingTopic, receivedSykmelding, loggingMeta)

    notifySyfoService(
        syfoserviceProducer = syfoserviceProducer,
        syfoserviceTopic = syfoserviceTopic,
        ediLoggId = sykmeldingId,
        sykmeldingId = receivedSykmelding.sykmelding.id,
        msgId = sykmeldingId,
        healthInformation = healthInformation,
        loggingMeta = loggingMeta
    )
    log.info("Message send to syfoService, {}", fields(loggingMeta))
}
