package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.coroutineScope
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_FORDELINGSOPPGAVE
import no.nav.syfo.wrapExceptions

class FordelingsOppgaveService(
    private val oppgaveService: OppgaveService
) {
    @KtorExperimentalAPI
    suspend fun handterJournalpostUtenBruker(
        journalpostId: String,
        gjelderUtland: Boolean,
        loggingMeta: LoggingMeta,
        sykmeldingId: String
    ) = coroutineScope {
        wrapExceptions(loggingMeta) {
            log.info("Oppretter fordelingsoppgave, {}", fields(loggingMeta))
            val oppgaveId = oppgaveService.opprettFordelingsOppgave(journalpostId = journalpostId, gjelderUtland = gjelderUtland, trackingId = sykmeldingId, loggingMeta = loggingMeta)

            if (oppgaveId != 0) {
                PAPIRSM_FORDELINGSOPPGAVE.inc()
                log.info("Opprettet fordelingsoppgave med {}, {} {}",
                    StructuredArguments.keyValue("oppgaveId", oppgaveId),
                    StructuredArguments.keyValue("journalpostId", journalpostId),
                    fields(loggingMeta)
                )
            }
        }
    }
}
