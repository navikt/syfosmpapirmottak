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
import no.nav.syfo.log
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.wrapExceptions

@KtorExperimentalAPI
class BehandlingService constructor(
    val safJournalpostClient: SafJournalpostClient,
    val aktoerIdClient: AktoerIdClient,
    val sakClient: SakClient,
    val oppgaveService: OppgaveService
) {
    suspend fun handleJournalpost(
        journalfoeringEvent: JournalfoeringHendelseRecord,
        loggingMeta: LoggingMeta,
        sykmeldingId: String
    ) = coroutineScope {
        wrapExceptions(loggingMeta) {
            val journalpostId = journalfoeringEvent.journalpostId.toString()

            if (journalfoeringEvent.temaNytt.toString() == "SYM" &&
                    journalfoeringEvent.mottaksKanal.toString() == "SKAN_NETS"
            // TODO skal vi dobbelt sjekke at dette er ein ny hendelse???
            ) {
                val requestLatency = REQUEST_TIME.startTimer()
                log.info("Received papirsykmelding, {}", fields(loggingMeta))
                val journalpost = safJournalpostClient.getJournalpostMetadata(journalpostId)
                        ?: error("Unable to find journalpost with id $journalpostId")

                log.debug("Response from saf graphql, {}", fields(loggingMeta))

                val aktoerIdPasient = aktoerIdClient.findAktorid(journalpost, sykmeldingId)
                val fnrPasient = aktoerIdClient.findFNR(journalpost, sykmeldingId)

                val sakId = sakClient.findOrCreateSak(sykmeldingId, aktoerIdPasient, loggingMeta)

                val createTaskResponse = oppgaveService.createOppgave(fnrPasient, aktoerIdPasient, sykmeldingId,
                        journalpostId, sykmeldingId, loggingMeta)

                log.info("Task created with {}, {} {}",
                        StructuredArguments.keyValue("oppgaveId", createTaskResponse.id),
                        StructuredArguments.keyValue("sakid", sakId),
                        fields(loggingMeta)
                )

                val currentRequestLatency = requestLatency.observeDuration()

                log.info("Finished processing took {}s, {}", StructuredArguments.keyValue("latency", currentRequestLatency), fields(loggingMeta))
            }
        }
    }
}
