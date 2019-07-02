package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.*
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.metrics.REQUEST_TIME
import java.util.*

@KtorExperimentalAPI
class BehandlingService constructor(
    val safJournalpostClient: SafJournalpostClient,
    val aktoerIdClient: AktoerIdClient,
    val sakClient: SakClient,
    val oppgaveService: OppgaveService
) {
    suspend fun handleJournalpost(
        journalfoeringEvent: JournalfoeringHendelseRecord
    ) {
        val sykmeldingId = UUID.randomUUID().toString()
        val journalpostId = journalfoeringEvent.journalpostId.toString()
        val hendelsesId = journalfoeringEvent.hendelsesId

        val logValues = arrayOf(
                StructuredArguments.keyValue("sykmeldingId", sykmeldingId),
                StructuredArguments.keyValue("journalpostId", journalpostId),
                StructuredArguments.keyValue("hendelsesId", hendelsesId)
        )
        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }

        try {
            if (journalfoeringEvent.temaNytt.toString() == "SYM" &&
                    journalfoeringEvent.mottaksKanal.toString() == "SKAN_NETS"
            // TODO skal vi dobbelt sjekke at dette er ein ny hendelse???
            ) {
                val requestLatency = REQUEST_TIME.startTimer()
                log.info("Received papirsykmelding, $logKeys", *logValues)
                val journalpost = safJournalpostClient.getJournalpostMetadata(journalpostId)
                        ?: error("Unable to find journalpost with id $journalpostId")

                log.debug("Response from saf graphql $logKeys", *logValues)

                val aktoerIdPasient = aktoerIdClient.findAktorid(journalpost, sykmeldingId)
                val fnrPasient = aktoerIdClient.findFNR(journalpost, sykmeldingId)

                val sakId = sakClient.findOrCreateSak(sykmeldingId, aktoerIdPasient, logKeys, logValues)

                val createTaskResponse = oppgaveService.createOppgave(fnrPasient, aktoerIdPasient, sykmeldingId,
                        journalpostId, sykmeldingId, logKeys, logValues)

                log.info("Task created with {}, {} $logKeys",
                        StructuredArguments.keyValue("oppgaveId", createTaskResponse.id),
                        StructuredArguments.keyValue("sakid", sakId),
                        *logValues
                )

                val currentRequestLatency = requestLatency.observeDuration()

                log.info("Finished processing ($logKeys) {}s", *logValues, StructuredArguments.keyValue("latency", currentRequestLatency))
            }
        } catch (e: Exception) {
            log.error("Exception caught while handling message $logKeys", *logValues, e)
            throw e
        }
    }
}