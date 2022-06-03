package no.nav.syfo.service

import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer

suspend fun handleOk(
    kafkaReceivedSykmeldingProducer: KafkaProducer<String, ReceivedSykmelding>,
    okSykmeldingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    sykmeldingId: String,
    dokArkivClient: DokArkivClient,
    journalpostid: String,
    loggingMeta: LoggingMeta
) {
    dokArkivClient.oppdaterOgFerdigstillJournalpost(journalpostId = journalpostid, fnr = receivedSykmelding.personNrPasient, sykmeldingId = sykmeldingId, behandler = receivedSykmelding.sykmelding.behandler, loggingMeta = loggingMeta)

    sendReceivedSykmeldingToKafka(kafkaReceivedSykmeldingProducer, okSykmeldingTopic, receivedSykmelding, loggingMeta)
}
