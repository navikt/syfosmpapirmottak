package no.nav.syfo.service

import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toReceivedSykmeldingWithValidation
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer

suspend fun handleOk(
    kafkaReceivedSykmeldingProducer: KafkaProducer<String, ReceivedSykmeldingWithValidation>,
    okSykmeldingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    sykmeldingId: String,
    dokArkivClient: DokArkivClient,
    journalpostid: String,
    loggingMeta: LoggingMeta,
) {
    dokArkivClient.oppdaterOgFerdigstillJournalpost(
        journalpostId = journalpostid,
        fnr = receivedSykmelding.personNrPasient,
        sykmeldingId = sykmeldingId,
        behandler = receivedSykmelding.sykmelding.behandler,
        loggingMeta = loggingMeta
    )

    sendReceivedSykmeldingToKafka(
        kafkaReceivedSykmeldingProducer,
        okSykmeldingTopic,
        receivedSykmelding.toReceivedSykmeldingWithValidation(
            ValidationResult(Status.OK, emptyList())
        ),
        loggingMeta
    )
}
