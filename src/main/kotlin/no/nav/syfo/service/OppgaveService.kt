package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

class OppgaveService @KtorExperimentalAPI constructor(
    private val oppgaveClient: OppgaveClient
) {
    @KtorExperimentalAPI
    suspend fun opprettOppgave(
        aktoerIdPasient: String,
        sakId: String,
        journalpostId: String,
        gjelderUtland: Boolean,
        trackingId: String,
        loggingMeta: LoggingMeta
    ): OppgaveResultat {

        log.info("Oppretter oppgave for {}", fields(loggingMeta))

        return oppgaveClient.opprettOppgave(sakId, journalpostId,
                aktoerIdPasient, gjelderUtland, trackingId, loggingMeta)
    }

    @KtorExperimentalAPI
    suspend fun opprettFordelingsOppgave(
        journalpostId: String,
        gjelderUtland: Boolean,
        trackingId: String,
        loggingMeta: LoggingMeta
    ): OppgaveResultat {

        log.info("Oppretter fordelingsoppgave for {}", fields(loggingMeta))

        return oppgaveClient.opprettFordelingsOppgave(journalpostId, gjelderUtland, trackingId, loggingMeta)
    }
}
