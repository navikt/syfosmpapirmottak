package no.nav.syfo.utland

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.DokumentMedTittel
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTLAND
import no.nav.syfo.metrics.SYK_DIG_OPPGAVER
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.service.BREVKODE_EGENERKLARING_UTENLANDSK_SYKMELDING
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.util.LoggingMeta

const val NAV_OSLO = "0393"

class UtenlandskSykmeldingService(
    private val oppgaveService: OppgaveService,
    private val sykDigProducer: SykDigProducer,
    private val cluster: String,
) {
    suspend fun behandleUtenlandskSykmelding(
        journalpostId: String,
        dokumentInfoId: String,
        dokumenter: List<DokumentMedTittel>,
        pasient: PdlPerson?,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ) {
        log.info("Mottatt utenlandsk papirsykmelding, {}", fields(loggingMeta))

        PAPIRSM_MOTTATT_UTLAND.inc()

        if (pasient?.aktorId == null || pasient.fnr == null) {
            oppgaveService.opprettFordelingsOppgave(
                journalpostId = journalpostId,
                gjelderUtland = true,
                trackingId = sykmeldingId,
                loggingMeta = loggingMeta
            )
        } else {

            val sykmeldingsdokument =
                dokumenter.firstOrNull {
                    it.brevkode == BREVKODE_EGENERKLARING_UTENLANDSK_SYKMELDING
                }

            if (sykmeldingsdokument == null) {
                log.info("journalpost $journalpostId har ikke utenlandsk sykmelding includert")
                return
            }

            val oppgave =
                oppgaveService.opprettOppgave(
                    aktoerIdPasient = pasient.aktorId,
                    journalpostId = journalpostId,
                    gjelderUtland = true,
                    trackingId = sykmeldingId,
                    loggingMeta = loggingMeta,
                )
            oppgave?.let {
                log.info(
                    "Oppgave med id ${it.oppgaveId} sendt til enhet ${it.tildeltEnhetsnr}, " +
                        "antall dokumenter: ${dokumenter.size}",
                )
            }
            if (
                oppgave?.oppgaveId != null &&
                    behandlesISykDig(oppgave.tildeltEnhetsnr, oppgave.oppgaveId, loggingMeta)
            ) {
                sykDigProducer.send(
                    sykmeldingId,
                    DigitaliseringsoppgaveKafka(
                        oppgaveId = oppgave.oppgaveId.toString(),
                        fnr = pasient.fnr,
                        journalpostId = journalpostId,
                        dokumentInfoId = sykmeldingsdokument.dokumentInfoId,
                        dokumenter = dokumenter.map { DokumentKafka(it.tittel, it.dokumentInfoId) },
                        type = "UTLAND",
                    ),
                    loggingMeta,
                )
                SYK_DIG_OPPGAVER.inc()
            }
        }
    }

    fun behandlesISykDig(
        tildeltEnhetsnr: String?,
        oppgaveId: Int,
        loggingMeta: LoggingMeta
    ): Boolean {
        return if (cluster == "dev-gcp") {
            log.info(
                "Sender utenlandsk sykmelding til syk-dig i dev med oppgaveId: $oppgaveId {}",
                fields(loggingMeta)
            )
            true
        } else {
            if (tildeltEnhetsnr == NAV_OSLO) {
                log.info(
                    "Sender utenlandsk sykmelding til syk-dig med oppgaveId: $oppgaveId {}",
                    fields(loggingMeta)
                )
                true
            } else {
                false
            }
        }
    }
}
