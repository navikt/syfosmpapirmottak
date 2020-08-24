package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MOTTATT
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions
import org.apache.kafka.clients.producer.KafkaProducer

@KtorExperimentalAPI
class BehandlingService(
    private val safJournalpostClient: SafJournalpostClient,
    private val sykmeldingService: SykmeldingService,
    private val utenlandskSykmeldingService: UtenlandskSykmeldingService,
    private val pdlPersonService: PdlPersonService
) {
    suspend fun handleJournalpost(
        journalfoeringEvent: JournalfoeringHendelseRecord,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
        syfoserviceProducer: MessageProducer,
        session: Session,
        sm2013AutomaticHandlingTopic: String,
        kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
        dokArkivClient: DokArkivClient,
        kafkaproducerPapirSmRegistering: KafkaProducer<String, PapirSmRegistering>,
        sm2013SmregistreringTopic: String,
        cluster: String
    ) {
        wrapExceptions(loggingMeta) {
            val journalpostId = journalfoeringEvent.journalpostId.toString()

            if (journalfoeringEvent.temaNytt.toString() == "SYM" &&
                    (journalfoeringEvent.mottaksKanal.toString() == "SKAN_NETS" || journalfoeringEvent.mottaksKanal.toString() == "SKAN_IM") &&
                    journalfoeringEvent.hendelsesType.toString() == "MidlertidigJournalført"
            ) {
                val requestLatency = REQUEST_TIME.startTimer()
                PAPIRSM_MOTTATT.inc()
                log.info("Mottatt papirsykmelding fra mottakskanal {}, {}", journalfoeringEvent.mottaksKanal, fields(loggingMeta))
                val journalpostMetadata = safJournalpostClient.getJournalpostMetadata(journalpostId, loggingMeta)
                        ?: throw IllegalStateException("Unable to find journalpost with id $journalpostId")

                log.debug("Response from saf graphql, {}", fields(loggingMeta))

                if (journalpostMetadata.jpErIkkeJournalfort) {
                    val pasient = journalpostMetadata.bruker.let {
                        if (it.id.isNullOrEmpty() || it.type.isNullOrEmpty()) {
                            log.info("Mottatt papirsykmelding der bruker mangler, {}", fields(loggingMeta))
                            null
                        } else {
                            hentBrukerIdFraJournalpost(journalpostMetadata)?.let { pdlPersonService.getPersonnavn(it, loggingMeta) }
                        }
                    }

                    if (journalpostMetadata.gjelderUtland) {
                        utenlandskSykmeldingService.behandleUtenlandskSykmelding(journalpostId = journalpostId, pasient = pasient, loggingMeta = loggingMeta, sykmeldingId = sykmeldingId)
                    } else {
                        sykmeldingService.behandleSykmelding(
                                journalpostId = journalpostId,
                                pasient = pasient,
                                datoOpprettet = journalpostMetadata.datoOpprettet,
                                dokumentInfoId = journalpostMetadata.dokumentInfoId,
                                loggingMeta = loggingMeta,
                                sykmeldingId = sykmeldingId,
                                syfoserviceProducer = syfoserviceProducer,
                                session = session,
                                sm2013AutomaticHandlingTopic = sm2013AutomaticHandlingTopic,
                                kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmelding,
                                dokArkivClient = dokArkivClient,
                                kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                                sm2013SmregistreringTopic = sm2013SmregistreringTopic,
                                cluster = cluster
                        )
                    }
                } else {
                    log.info("Journalpost med id {} er allerede journalført, {}", journalpostId, fields(loggingMeta))
                }
                val currentRequestLatency = requestLatency.observeDuration()

                log.info("Finished processing took {}s, {}", StructuredArguments.keyValue("latency", currentRequestLatency), fields(loggingMeta))
            } else {
                log.info("Mottatt jp som ikke treffer filter, tema = ${journalfoeringEvent.temaNytt}, mottakskanal = ${journalfoeringEvent.mottaksKanal}, hendelsestype = ${journalfoeringEvent.hendelsesType}, journalpostid = $journalpostId")
            }
        }
    }

    fun hentBrukerIdFraJournalpost(
        journalpost: JournalpostMetadata
    ): String? {
        val bruker = journalpost.bruker
        val brukerId = bruker.id ?: throw IllegalStateException("Journalpost mangler brukerid, skal ikke kunne skje")
        return if (bruker.type == "AKTOERID" || bruker.type == "FNR") {
            brukerId
        } else
            return null
    }
}
