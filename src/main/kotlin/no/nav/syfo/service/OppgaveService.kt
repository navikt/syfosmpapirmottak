package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.OppgaveResponse
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_FORDELINGSOPPGAVE
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTEN_BRUKER
import no.nav.syfo.metrics.PAPIRSM_OPPGAVE
import no.nav.syfo.objectMapper
import no.nav.syfo.securelog
import no.nav.syfo.util.LoggingMeta

class OppgaveService(
    private val oppgaveClient: OppgaveClient,
) {
    suspend fun opprettOppgave(
        aktoerIdPasient: String,
        journalpostId: String,
        gjelderUtland: Boolean,
        trackingId: String,
        loggingMeta: LoggingMeta,
        type: String = "JFR",
    ): OppgaveResultat? {
        log.info("Oppretter oppgave for {}", fields(loggingMeta))

        val oppgave =
            oppgaveClient.opprettOppgave(
                journalpostId,
                aktoerIdPasient,
                gjelderUtland,
                trackingId,
                loggingMeta,
                type
            )

        return if (!oppgave.duplikat) {
            log.info(
                "Opprettet oppgave for utenlandsk sykmelding med {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgave.oppgaveId),
                fields(loggingMeta),
            )
            PAPIRSM_OPPGAVE.inc()
            oppgave
        } else {
            log.info(
                "duplikat oppgave med {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgave.oppgaveId),
                fields(loggingMeta)
            )
            null
        }
    }

    suspend fun hentOppgave(
        journalpostId: String,
        sykmeldingId: String,
        type: String = "JFR",
    ): OppgaveResponse {
        return oppgaveClient.hentOppgave(
            oppgavetype = "JFR",
            journalpostId = journalpostId,
            msgId = sykmeldingId
        )
    }

    suspend fun opprettFordelingsOppgave(
        journalpostId: String,
        gjelderUtland: Boolean,
        trackingId: String,
        loggingMeta: LoggingMeta,
    ) {
        PAPIRSM_MOTTATT_UTEN_BRUKER.inc()
        log.info(
            "Papirsykmelding mangler bruker, oppretter fordelingsoppgave: {}",
            fields(loggingMeta)
        )

        val oppgave =
            oppgaveClient.opprettFordelingsOppgave(
                journalpostId,
                gjelderUtland,
                trackingId,
                loggingMeta
            )

        if (!oppgave.duplikat) {
            PAPIRSM_FORDELINGSOPPGAVE.inc()
            securelog.info(
                "Opprettet fordelingsoppgave med {}, {} {} {}",
                StructuredArguments.keyValue("oppgaveId", oppgave.oppgaveId),
                StructuredArguments.keyValue("journalpostId", journalpostId),
                StructuredArguments.keyValue("oppgave", objectMapper.writeValueAsString(oppgave)),
                fields(loggingMeta),
            )
            log.info(
                "Opprettet fordelingsoppgave med {}, {} {}",
                StructuredArguments.keyValue("oppgaveId", oppgave.oppgaveId),
                StructuredArguments.keyValue("journalpostId", journalpostId),
                fields(loggingMeta),
            )
        }
    }
}
