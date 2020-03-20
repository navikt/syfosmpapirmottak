package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MOTTATT
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions

@KtorExperimentalAPI
class BehandlingService constructor(
    private val safJournalpostClient: SafJournalpostClient,
    private val aktoerIdClient: AktoerIdClient,
    private val sykmeldingService: SykmeldingService,
    private val utenlandskSykmeldingService: UtenlandskSykmeldingService
) {
    suspend fun handleJournalpost(
        journalfoeringEvent: JournalfoeringHendelseRecord,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
        syfoserviceProducer: MessageProducer,
        session: Session
    ) {
        wrapExceptions(loggingMeta) {
            val journalpostId = journalfoeringEvent.journalpostId.toString()

            if (journalfoeringEvent.temaNytt.toString() == "SYM" &&
                    journalfoeringEvent.mottaksKanal.toString() == "SKAN_NETS" &&
                    journalfoeringEvent.hendelsesType.toString() == "MidlertidigJournalført"
            ) {
                val requestLatency = REQUEST_TIME.startTimer()
                PAPIRSM_MOTTATT.inc()
                log.info("Mottatt papirsykmelding, {}", fields(loggingMeta))
                val journalpostMetadata = safJournalpostClient.getJournalpostMetadata(journalpostId, loggingMeta)
                        ?: throw IllegalStateException("Unable to find journalpost with id $journalpostId")

                log.debug("Response from saf graphql, {}", fields(loggingMeta))

                if (journalpostMetadata.jpErIkkeJournalfort) {
                    var aktorId: String? = null
                    var fnr: String? = null
                    if (journalpostMetadata.bruker.id.isNullOrEmpty() || journalpostMetadata.bruker.type.isNullOrEmpty()) {
                        log.info("Mottatt papirsykmelding der bruker mangler, {}", fields(loggingMeta))
                    } else {
                        aktorId = hentAktoridFraJournalpost(journalpostMetadata, sykmeldingId)
                        fnr = hentFnrFraJournalpost(journalpostMetadata, sykmeldingId)
                    }

                    if (journalpostMetadata.gjelderUtland) {
                        utenlandskSykmeldingService.behandleUtenlandskSykmelding(journalpostId = journalpostId, fnr = fnr, aktorId = aktorId, loggingMeta = loggingMeta, sykmeldingId = sykmeldingId)
                    } else {
                        sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnr,
                                aktorId = aktorId, datoOpprettet = journalpostMetadata.datoOpprettet,
                                dokumentInfoId = journalpostMetadata.dokumentInfoId,
                                loggingMeta = loggingMeta, sykmeldingId = sykmeldingId,
                                syfoserviceProducer = syfoserviceProducer,
                                session = session)
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

    suspend fun hentAktoridFraJournalpost(
        journalpost: JournalpostMetadata,
        sykmeldingId: String
    ): String? {
        val bruker = journalpost.bruker
        val brukerId = bruker.id ?: throw IllegalStateException("Journalpost mangler brukerid, skal ikke kunne skje")
        return if (bruker.type == "AKTOERID") {
            brukerId
        } else {
            aktoerIdClient.finnAktorid(brukerId, sykmeldingId)
        }
    }

    suspend fun hentFnrFraJournalpost(
        journalpost: JournalpostMetadata,
        sykmeldingId: String
    ): String? {
        val bruker = journalpost.bruker
        val brukerId = bruker.id ?: throw IllegalStateException("Journalpost mangler brukerid, skal ikke kunne skje")
        return if (bruker.type == "FNR") {
            brukerId
        } else {
            aktoerIdClient.finnFnr(brukerId, sykmeldingId)
        }
    }
}
