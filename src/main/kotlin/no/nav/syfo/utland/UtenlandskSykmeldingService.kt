package no.nav.syfo.utland

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTLAND
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.util.LoggingMeta

const val NAV_OSLO = "0301"
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
            val oppgave = oppgaveService.opprettOppgave(
                aktoerIdPasient = pasient.aktorId, journalpostId = journalpostId, gjelderUtland = true, trackingId = sykmeldingId, loggingMeta = loggingMeta
            )
            oppgave?.let { log.info("Oppgave med id ${it.oppgaveId} sendt til enhet ${it.tildeltEnhetsnr}") }
            if (cluster == "dev-gcp" && oppgave?.oppgaveId != null) {
                log.info("Sender utenlandsk sykmelding til syk-dig i dev {}", fields(loggingMeta))
                sykDigProducer.send(
                    sykmeldingId,
                    DigitaliseringsoppgaveKafka(
                        oppgaveId = oppgave.oppgaveId.toString(),
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

    fun behandlesISykDig(tildeltEnhetsnr: String?, fnr: String, loggingMeta: LoggingMeta): Boolean {
        return if (cluster == "dev-gcp") {
            true
        } else {
            if (tildeltEnhetsnr == NAV_OSLO && trefferAldersfilter(fnr, Filter.ETTER1995)) {
                log.info("Sender utenlandsk sykmelding til syk-dig i dev {}", fields(loggingMeta))
                true
            } else {
                false
            }
        }
    }
}
