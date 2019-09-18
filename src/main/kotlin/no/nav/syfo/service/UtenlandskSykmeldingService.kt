package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.SakClient
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_FORDELINGSOPPGAVE
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTEN_BRUKER
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTLAND
import no.nav.syfo.metrics.PAPIRSM_OPPGAVE

@KtorExperimentalAPI
class UtenlandskSykmeldingService constructor(
    private val sakClient: SakClient,
    private val oppgaveService: OppgaveService
) {
    suspend fun behandleUtenlandskSykmelding(
        journalpostId: String,
        fnr: String?,
        aktorId: String?,
        loggingMeta: LoggingMeta,
        sykmeldingId: String
    ) {
        log.info("Mottatt utenlandsk papirsykmelding, {}", fields(loggingMeta))
        PAPIRSM_MOTTATT_UTLAND.inc()

        if (aktorId.isNullOrEmpty() || fnr.isNullOrEmpty()) {
            PAPIRSM_MOTTATT_UTEN_BRUKER.inc()
            log.info("Utenlandsk papirsykmelding mangler bruker, oppretter fordelingsoppgave: {}", fields(loggingMeta))

            val oppgave = oppgaveService.opprettFordelingsOppgave(journalpostId = journalpostId, gjelderUtland = true, trackingId = sykmeldingId, loggingMeta = loggingMeta)

            if (!oppgave.duplikat) {
                PAPIRSM_FORDELINGSOPPGAVE.inc()
                log.info("Opprettet fordelingsoppgave med {}, {} {}",
                    StructuredArguments.keyValue("oppgaveId", oppgave.oppgaveId),
                    StructuredArguments.keyValue("journalpostId", journalpostId),
                    fields(loggingMeta)
                )
            }
        } else {
            val sakId = sakClient.finnEllerOpprettSak(sykmeldingsId = sykmeldingId, aktorId = aktorId, loggingMeta = loggingMeta)

            val oppgave = oppgaveService.opprettOppgave(fnrPasient = fnr, aktoerIdPasient = aktorId, sakId = sakId,
                journalpostId = journalpostId, gjelderUtland = true, trackingId = sykmeldingId, loggingMeta = loggingMeta)

            if (!oppgave.duplikat) {
                log.info("Opprettet oppgave for utenlandsk sykmelding med {}, {} {}",
                    StructuredArguments.keyValue("oppgaveId", oppgave.oppgaveId),
                    StructuredArguments.keyValue("sakid", sakId),
                    fields(loggingMeta)
                )
                PAPIRSM_OPPGAVE.inc()
            }
        }
    }
}
