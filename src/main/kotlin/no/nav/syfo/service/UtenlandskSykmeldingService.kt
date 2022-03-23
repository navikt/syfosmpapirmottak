package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTLAND
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.LoggingMeta

class UtenlandskSykmeldingService(
    private val oppgaveService: OppgaveService
) {
    suspend fun behandleUtenlandskSykmelding(
        journalpostId: String,
        pasient: PdlPerson?,
        loggingMeta: LoggingMeta,
        sykmeldingId: String
    ) {

        log.info("Mottatt utenlandsk papirsykmelding, {}", fields(loggingMeta))
        PAPIRSM_MOTTATT_UTLAND.inc()

        if (pasient?.aktorId == null || pasient.fnr == null) {
            oppgaveService.opprettFordelingsOppgave(journalpostId = journalpostId, gjelderUtland = true, trackingId = sykmeldingId, loggingMeta = loggingMeta)
        } else {
            oppgaveService.opprettOppgave(
                aktoerIdPasient = pasient.aktorId, journalpostId = journalpostId, gjelderUtland = true, trackingId = sykmeldingId, loggingMeta = loggingMeta
            )
        }
    }
}
