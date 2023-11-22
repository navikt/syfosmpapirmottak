package no.nav.syfo.opprettsykmelding

import java.time.Duration
import java.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.createListener
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.opprettsykmelding.model.OpprettSykmeldingRecord
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.securelog
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

@OptIn(DelicateCoroutinesApi::class)
fun startOpprettSykmeldingConsumer(
    environment: Environment,
    applicationState: ApplicationState,
    sykmeldingService: SykmeldingService,
    safJournalpostClient: SafJournalpostClient,
    pdlPersonService: PdlPersonService
) {

    val consumer = "opprett-sykmelding-consumer"
    val consumerProperties =
        KafkaUtils.getAivenKafkaConfig(consumer)
            .toConsumerConfig(
                groupId = consumer,
                valueDeserializer = OpprettSykmeldingDeserializer::class
            )

    val kafkaConsumer =
        KafkaConsumer(consumerProperties, StringDeserializer(), OpprettSykmeldingDeserializer())

    OpprettSykmeldingService(
            kafkaConsumer = kafkaConsumer,
            sykmeldingService = sykmeldingService,
            env = environment,
            applicationState = applicationState,
            safJournalpostClient = safJournalpostClient,
            pdlPersonService = pdlPersonService,
        )
        .start()
}

class OpprettSykmeldingService(
    private val kafkaConsumer: KafkaConsumer<String, OpprettSykmeldingRecord>,
    private val sykmeldingService: SykmeldingService,
    private val env: Environment,
    private val applicationState: ApplicationState,
    private val safJournalpostClient: SafJournalpostClient,
    private val pdlPersonService: PdlPersonService
) {
    companion object {
        private val log = LoggerFactory.getLogger(OpprettSykmeldingService::class.java)
    }

    private val journalPostQuery =
        SafJournalpostClient::class
            .java
            .getResource("/graphql/findJournalpost.graphql")!!
            .readText()
            .replace(Regex("[\n\t]"), "")

    @DelicateCoroutinesApi
    fun start() {
        createListener(applicationState) { consumeTopic() }
    }

    suspend fun consumeTopic() {
        kafkaConsumer.subscribe(listOf(env.opprettSykmeldingTopic))
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofMillis(1000)).forEach { consumerRecord ->
                try {
                    handleOpprettSykmelding(consumerRecord)
                }catch (e: Exception) {
                    log.error("error in handeling message", e)
                   if (env.cluster == "dev-gcp"){
                       log.warn("skipping error in dev")
                   } else {
                       throw e
                   }
                }
            }
        }
    }

    private suspend fun handleOpprettSykmelding(consumerRecord: ConsumerRecord<String, OpprettSykmeldingRecord>) {
        val opprettSykmeldingRecord = consumerRecord.value()
        val sykmeldingId = UUID.randomUUID().toString()
        val loggingMeta =
            LoggingMeta(
                sykmeldingId = sykmeldingId,
                journalpostId = opprettSykmeldingRecord.data.journalpostId,
                hendelsesId = opprettSykmeldingRecord.data.journalpostId,
            )
        val journalpostId = opprettSykmeldingRecord.data.journalpostId
        log.info("received opprett sykmelding for $journalpostId {}", fields(loggingMeta))
        securelog.info(
            "received opprett sykmelding message $opprettSykmeldingRecord {}",
            fields(loggingMeta)
        )
        val journalpostMetadata =
            safJournalpostClient.getJournalpostMetadata(
                journalpostId,
                journalPostQuery,
                loggingMeta,
            )
                ?: throw IllegalStateException(
                    "Unable to find journalpost with id $journalpostId",
                )

        val fnrEllerAktorId =
            when (journalpostMetadata.bruker.type) {
                "ORGNR" -> throw IllegalStateException("Bruker id is ORGNR")
                else -> journalpostMetadata.bruker.id
            }
                ?: throw IllegalStateException("Bruker id is null")

        val pasient = pdlPersonService.getPdlPerson(fnrEllerAktorId, loggingMeta)
        sykmeldingService.behandleSykmelding(
            journalpostId = journalpostId,
            pasient = pasient,
            datoOpprettet = journalpostMetadata.datoOpprettet,
            dokumentInfoIdPdf = journalpostMetadata.dokumentInfoIdPdf,
            dokumentInfoId = journalpostMetadata.dokumentInfoId,
            temaEndret = false,
            loggingMeta = loggingMeta,
            sykmeldingId = sykmeldingId,
        )
    }
}
