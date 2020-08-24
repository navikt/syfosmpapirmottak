package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.SakClient
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTLAND
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class UtenlandskSykmeldingService constructor(
    private val sakClient: SakClient,
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
            val sakId = sakClient.finnEllerOpprettSak(sykmeldingsId = sykmeldingId, aktorId = pasient.aktorId, loggingMeta = loggingMeta)
            oppgaveService.opprettOppgave(aktoerIdPasient = pasient.aktorId, sakId = sakId,
                journalpostId = journalpostId, gjelderUtland = true, trackingId = sykmeldingId, loggingMeta = loggingMeta)
        }
    }
}
