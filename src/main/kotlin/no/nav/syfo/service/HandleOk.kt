package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
suspend fun handleOk(
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    sm2013AutomaticHandlingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    session: Session,
    syfoserviceProducer: MessageProducer,
    sykmeldingId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    dokArkivClient: DokArkivClient,
    journalpostid: String,
    loggingMeta: LoggingMeta
) {

    dokArkivClient.oppdaterOgFerdigstillJournalpost(journalpostId = journalpostid, fnr = receivedSykmelding.personNrPasient, sykmeldingId = sykmeldingId, hprnummer = receivedSykmelding.sykmelding.behandler.hpr!!, loggingMeta = loggingMeta)
    kafkaproducerreceivedSykmelding.send(ProducerRecord(sm2013AutomaticHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
    log.info("Message send to kafka {}, {}", sm2013AutomaticHandlingTopic, fields(loggingMeta))

    notifySyfoService(session = session, receiptProducer = syfoserviceProducer, ediLoggId = sykmeldingId,
            sykmeldingId = receivedSykmelding.sykmelding.id, msgId = sykmeldingId, healthInformation = healthInformation)
    log.info("Message send to syfoService, {}", fields(loggingMeta))
}
