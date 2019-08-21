package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.coroutineScope
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MOTTATT
import no.nav.syfo.metrics.PAPIRSM_OPPGAVE
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.wrapExceptions

@KtorExperimentalAPI
class BehandlingService constructor(
    val safJournalpostClient: SafJournalpostClient,
    val aktoerIdClient: AktoerIdClient,
    val sakClient: SakClient,
    val oppgaveService: OppgaveService,
    val fordelingsOppgaveService: FordelingsOppgaveService
) {
    suspend fun handleJournalpost(
        journalfoeringEvent: JournalfoeringHendelseRecord,
        loggingMeta: LoggingMeta,
        sykmeldingId: String
    ) = coroutineScope {
        wrapExceptions(loggingMeta) {
            val journalpostId = journalfoeringEvent.journalpostId.toString()

            if (journalfoeringEvent.temaNytt.toString() == "SYM" &&
                    journalfoeringEvent.mottaksKanal.toString() == "SKAN_NETS" &&
                    journalfoeringEvent.hendelsesType == "MidlertidigJournalført"
            ) {
                val requestLatency = REQUEST_TIME.startTimer()
                PAPIRSM_MOTTATT.inc()
                log.info("Mottatt papirsykmelding, {}", fields(loggingMeta))
                val journalpostMetadata = safJournalpostClient.getJournalpostMetadata(journalpostId)
                        ?: throw IllegalStateException("Unable to find journalpost with id $journalpostId")

                log.debug("Response from saf graphql, {}", fields(loggingMeta))

                if (!journalpostMetadata.bruker.id.isNullOrEmpty() && !journalpostMetadata.bruker.type.isNullOrEmpty()) {
                    val aktoerIdPasient = hentAktoridFraJournalpost(journalpostMetadata, sykmeldingId)
                    val fnrPasient = hentFnrFraJournalpost(journalpostMetadata, sykmeldingId)

                    val sakId = sakClient.finnEllerOpprettSak(sykmeldingsId = sykmeldingId, aktorId = aktoerIdPasient, loggingMeta = loggingMeta)

                    val oppgaveId = oppgaveService.opprettOppgave(fnrPasient = fnrPasient, aktoerIdPasient = aktoerIdPasient, sakId = sakId,
                        journalpostId = journalpostId, trackingId = sykmeldingId, loggingMeta = loggingMeta)

                    log.info("Opprettet oppgave med {}, {} {}",
                        StructuredArguments.keyValue("oppgaveId", oppgaveId),
                        StructuredArguments.keyValue("sakid", sakId),
                        fields(loggingMeta)
                    )
                    PAPIRSM_OPPGAVE.inc()
                } else {
                    fordelingsOppgaveService.handterJournalpostUtenBruker(journalpostId, loggingMeta, sykmeldingId)
                }

                val currentRequestLatency = requestLatency.observeDuration()

                log.info("Finished processing took {}s, {}", StructuredArguments.keyValue("latency", currentRequestLatency), fields(loggingMeta))
            }
        }
    }

    suspend fun hentAktoridFraJournalpost(
        journalpost: JournalpostMetadata,
        sykmeldingId: String
    ): String {
        val bruker = journalpost.bruker ?: throw IllegalStateException("Journalpost mangler bruker")
        val brukerId = bruker.id ?: throw IllegalStateException("Journalpost mangler brukerid")
        return if (bruker.type == "AKTOERID") {
            brukerId
        } else {
            aktoerIdClient.finnAktorid(brukerId, sykmeldingId)
        }
    }

    suspend fun hentFnrFraJournalpost(
        journalpost: JournalpostMetadata,
        sykmeldingId: String
    ): String {
        val bruker = journalpost.bruker ?: throw IllegalStateException("Journalpost mangler bruker")
        val brukerId = bruker.id ?: throw IllegalStateException("Journalpost mangler brukerid")
        return if (bruker.type == "FNR") {
            brukerId
        } else {
            aktoerIdClient.finnFnr(brukerId, sykmeldingId)
        }
    }
}
