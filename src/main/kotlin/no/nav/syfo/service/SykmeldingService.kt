package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_FORDELINGSOPPGAVE
import no.nav.syfo.metrics.PAPIRSM_MOTTATT
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTEN_BRUKER
import no.nav.syfo.metrics.PAPIRSM_OPPGAVE

@KtorExperimentalAPI
class SykmeldingService constructor(
    private val sakClient: SakClient,
    private val oppgaveService: OppgaveService,
    private val safDokumentClient: SafDokumentClient
) {
    suspend fun behandleSykmelding(
        journalpostId: String,
        fnr: String?,
        aktorId: String?,
        dokumentInfoId: String?,
        loggingMeta: LoggingMeta,
        sykmeldingId: String
    ) {
        log.info("Mottatt papirsykmelding, {}", fields(loggingMeta))
        PAPIRSM_MOTTATT.inc()

        dokumentInfoId?.let {
            try {
                safDokumentClient.hentDokument(journalpostId = journalpostId, dokumentInfoId = it, msgId = sykmeldingId, loggingMeta = loggingMeta)
            } catch (e: Exception) {
                log.warn("Kunne ikke hente OCR-dokument: ${e.message}, {}", fields(loggingMeta))
            }
        }

        if (aktorId.isNullOrEmpty() || fnr.isNullOrEmpty()) {
            PAPIRSM_MOTTATT_UTEN_BRUKER.inc()
            log.info("Papirsykmelding mangler bruker, oppretter fordelingsoppgave: {}", fields(loggingMeta))

            val oppgave = oppgaveService.opprettFordelingsOppgave(journalpostId = journalpostId, gjelderUtland = false, trackingId = sykmeldingId, loggingMeta = loggingMeta)

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
    }
}
