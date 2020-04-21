package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.finnFristForFerdigstillingAvOppgave
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
suspend fun handleManuell(
    kafkaManuelTaskProducer: KafkaProducer<String, ProduceTask>,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    sm2013ManualHandlingTopic: String,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    sm2013BehandlingsUtfallTopic: String,
    syfoserviceProducer: MessageProducer,
    session: Session,
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    dokArkivClient: DokArkivClient,
    journalpostid: String,
    loggingMeta: LoggingMeta
) {

    dokArkivClient.ferdigStillJournalpost(journalpostid, receivedSykmelding.sykmelding.id, loggingMeta)

    log.info("Sending manuell oppgave to syfosmoppgave {}", fields(loggingMeta))
    opprettOppgave(kafkaManuelTaskProducer, receivedSykmelding, validationResult, loggingMeta)

    notifySyfoService(session = session, receiptProducer = syfoserviceProducer, ediLoggId = receivedSykmelding.navLogId,
            sykmeldingId = receivedSykmelding.sykmelding.id, msgId = receivedSykmelding.sykmelding.msgId, healthInformation = healthInformation)
    log.info("Message send to syfoService, {}", fields(loggingMeta))

    kafkaproducerreceivedSykmelding.send(ProducerRecord(sm2013ManualHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
    log.info("Message send to kafka {}, {}", sm2013ManualHandlingTopic, fields(loggingMeta))

    sendValidationResult(validationResult, kafkaproducervalidationResult, sm2013BehandlingsUtfallTopic, receivedSykmelding, loggingMeta)
}

fun sendValidationResult(
    validationResult: ValidationResult,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    sm2013BehandlingsUtfallToipic: String,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta
) {

    kafkaproducervalidationResult.send(
            ProducerRecord(sm2013BehandlingsUtfallToipic, receivedSykmelding.sykmelding.id, validationResult)
    )
    log.info("Validation results send to kafka {}, {}", sm2013BehandlingsUtfallToipic, fields(loggingMeta))
}

fun opprettOppgave(
    kafkaProducer: KafkaProducer<String, ProduceTask>,
    receivedSykmelding: ReceivedSykmelding,
    results: ValidationResult,
    loggingMeta: LoggingMeta
) {
    kafkaProducer.send(
            ProducerRecord(
                    "aapen-syfo-oppgave-produserOppgave",
                    receivedSykmelding.sykmelding.id,
                    ProduceTask().apply {
                        messageId = receivedSykmelding.msgId
                        aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
                        tildeltEnhetsnr = ""
                        opprettetAvEnhetsnr = "9999"
                        behandlesAvApplikasjon = "FS22" // Gosys
                        orgnr = receivedSykmelding.legekontorOrgNr ?: ""
                        beskrivelse = "Manuell behandling av sykmelding grunnet følgende regler: ${results.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}"
                        temagruppe = "ANY"
                        tema = "SYM"
                        behandlingstema = "ANY"
                        oppgavetype = "BEH_EL_SYM"
                        behandlingstype = "ANY"
                        mappeId = 1
                        aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                        fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(4)))
                        prioritet = PrioritetType.NORM
                        metadata = mapOf()
                    }))

    log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave {}", fields(loggingMeta))
}
