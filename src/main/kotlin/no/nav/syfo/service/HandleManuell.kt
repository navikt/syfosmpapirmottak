package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.SakClient
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_OPPGAVE
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
suspend fun handleManuell(
    sakClient: SakClient,
    aktorId: String,
    sykmeldingId: String,
    oppgaveService: OppgaveService,
    journalpostId: String,
    loggingMeta: LoggingMeta
) {

    val sakId = sakClient.finnEllerOpprettSak(sykmeldingsId = sykmeldingId, aktorId = aktorId, loggingMeta = loggingMeta)

    val oppgave = oppgaveService.opprettOppgave(aktoerIdPasient = aktorId, sakId = sakId,
            journalpostId = journalpostId, gjelderUtland = false, trackingId = sykmeldingId, loggingMeta = loggingMeta)
    if (!oppgave.duplikat) {
        log.info("Opprettet oppgave med {}, {} {}",
                StructuredArguments.keyValue("oppgaveId", oppgave.oppgaveId),
                StructuredArguments.keyValue("sakid", sakId),
                fields(loggingMeta)
        )
        PAPIRSM_OPPGAVE.inc()
    }
}
