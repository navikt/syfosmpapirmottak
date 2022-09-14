package no.nav.syfo.utland

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTLAND
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.util.LoggingMeta

class UtenlandskSykmeldingService(
    private val oppgaveService: OppgaveService,
    private val sykDigProducer: SykDigProducer,
    private val cluster: String
) {
    suspend fun behandleUtenlandskSykmelding(
        journalpostId: String,
        dokumentInfoId: String,
        pasient: PdlPerson?,
        loggingMeta: LoggingMeta,
        sykmeldingId: String
    ) {

        log.info("Mottatt utenlandsk papirsykmelding, {}", fields(loggingMeta))
        PAPIRSM_MOTTATT_UTLAND.inc()

        if (pasient?.aktorId == null || pasient.fnr == null) {
            oppgaveService.opprettFordelingsOppgave(journalpostId = journalpostId, gjelderUtland = true, trackingId = sykmeldingId, loggingMeta = loggingMeta)
        } else {
            val oppgaveId = oppgaveService.opprettOppgave(
                aktoerIdPasient = pasient.aktorId, journalpostId = journalpostId, gjelderUtland = true, trackingId = sykmeldingId, loggingMeta = loggingMeta
            )
            if (cluster == "dev-gcp" && oppgaveId != null) {
                log.info("Sender utenlandsk sykmelding til syk-dig i dev {}", fields(loggingMeta))
                sykDigProducer.send(
                    sykmeldingId,
                    DigitaliseringsoppgaveKafka(
                        oppgaveId = oppgaveId.toString(),
                        fnr = pasient.fnr,
                        journalpostId = journalpostId,
                        dokumentInfoId = dokumentInfoId,
                        type = "UTLAND"
                    ),
                    loggingMeta
                )
            }
        }
    }
}
