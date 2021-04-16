package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.log
import no.nav.syfo.metrics.ENDRET_PAPIRSM_MOTTATT
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
    private val pdlPersonService: PdlPersonService,
    private val oppgaveService: OppgaveService
) {
    suspend fun handleJournalpost(
        journalfoeringEvent: JournalfoeringHendelseRecord,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
        sm2013AutomaticHandlingTopic: String,
        kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
        dokArkivClient: DokArkivClient,
        kafkaproducerPapirSmRegistering: KafkaProducer<String, PapirSmRegistering>,
        sm2013SmregistreringTopic: String,
    ) {
        wrapExceptions(loggingMeta) {
            val journalpostId = journalfoeringEvent.journalpostId.toString()

            if (journalfoeringEvent.temaNytt.toString() == "SYM" &&
                (journalfoeringEvent.mottaksKanal.toString() == "SKAN_NETS" || journalfoeringEvent.mottaksKanal.toString() == "SKAN_IM") &&
                (journalfoeringEvent.hendelsesType.toString() == "MidlertidigJournalført" || journalfoeringEvent.hendelsesType.toString() == "TemaEndret")
            ) {
                val requestLatency = REQUEST_TIME.startTimer()
                PAPIRSM_MOTTATT.inc()
                log.info("Mottatt papirsykmelding fra mottakskanal {}, {}", journalfoeringEvent.mottaksKanal, fields(loggingMeta))
                if (journalfoeringEvent.hendelsesType.toString() == "TemaEndret") {
                    ENDRET_PAPIRSM_MOTTATT.inc()
                    log.info("Mottatt endret journalpost {}", fields(loggingMeta))
                }
                val journalpostMetadata = safJournalpostClient.getJournalpostMetadata(journalpostId, loggingMeta)
                    ?: throw IllegalStateException("Unable to find journalpost with id $journalpostId")

                log.debug("Response from saf graphql, {}", fields(loggingMeta))

                if (journalpostMetadata.jpErIkkeJournalfort) {
                    val pasient = journalpostMetadata.bruker.let {
                        if (it.id.isNullOrEmpty() || it.type.isNullOrEmpty()) {
                            log.info("Mottatt papirsykmelding der bruker mangler, {}", fields(loggingMeta))
                            null
                        } else {
                            hentBrukerIdFraJournalpost(journalpostMetadata)?.let { pdlPersonService.getPdlPerson(it, loggingMeta) }
                        }
                    }

                    if (journalpostMetadata.gjelderUtland) {
                        utenlandskSykmeldingService.behandleUtenlandskSykmelding(journalpostId = journalpostId, pasient = pasient, loggingMeta = loggingMeta, sykmeldingId = sykmeldingId)
                    } else {
                        if (skalBehandleJournalpost(hendelsesType = journalfoeringEvent.hendelsesType.toString(), journalpostId = journalpostId, sykmeldingId = sykmeldingId, loggingMeta = loggingMeta)) {
                            sykmeldingService.behandleSykmelding(
                                journalpostId = journalpostId,
                                pasient = pasient,
                                datoOpprettet = journalpostMetadata.datoOpprettet,
                                dokumentInfoId = journalpostMetadata.dokumentInfoId,
                                loggingMeta = loggingMeta,
                                sykmeldingId = sykmeldingId,
                                sm2013AutomaticHandlingTopic = sm2013AutomaticHandlingTopic,
                                kafkaReceivedSykmeldingProducer = kafkaproducerreceivedSykmelding,
                                dokArkivClient = dokArkivClient,
                                kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                                sm2013SmregistreringTopic = sm2013SmregistreringTopic
                            )
                        }
                    }
                } else {
                    log.info("Journalpost med id {} er allerede journalført, {}", journalpostId, fields(loggingMeta))
                }
                val currentRequestLatency = requestLatency.observeDuration()

                log.info("Finished processing took {}s, {}", StructuredArguments.keyValue("latency", currentRequestLatency), fields(loggingMeta))
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

    suspend fun skalBehandleJournalpost(
        hendelsesType: String,
        journalpostId: String,
        sykmeldingId: String,
        loggingMeta: LoggingMeta
    ): Boolean {
        if (hendelsesType == "MidlertidigJournalført") {
            return true
        }
        val duplikatOppgave = oppgaveService.duplikatOppgave(
            journalpostId = journalpostId,
            trackingId = sykmeldingId,
            loggingMeta = loggingMeta
        )
        if (duplikatOppgave) {
            log.info("Oppgave for endret journalpost finnes fra før, ignorerer melding {}", fields(loggingMeta))
            return false
        }
        return true
    }
}
