package no.nav.syfo.utland

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.DokumentMedTittel
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.log
import no.nav.syfo.metrics.SYK_DIG_OPPGAVER
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.service.BREVKODE_EGENERKLARING_UTENLANDSK_SYKMELDING
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.SYKMELDING_TYPE
import no.nav.syfo.unleash.Unleash
import no.nav.syfo.util.LoggingMeta

const val NAV_OSLO = "0393"

class UtenlandskSykmeldingService(
    private val oppgaveService: OppgaveService,
    private val sykDigProducer: SykDigProducer,
    private val cluster: String,
    private val unleash: Unleash,
) {
    suspend fun behandleUtenlandskSykmelding(
        journalpostId: String,
        dokumentInfoId: String,
        dokumenter: List<DokumentMedTittel>,
        pasient: PdlPerson?,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
        type: SYKMELDING_TYPE,
    ) {
        if (pasient?.aktorId == null || pasient.fnr == null) {
            oppgaveService.opprettFordelingsOppgave(
                journalpostId = journalpostId,
                gjelderUtland = true,
                trackingId = sykmeldingId,
                loggingMeta = loggingMeta
            )
            return
        }

        val oppgave =
            when (type) {
                SYKMELDING_TYPE.UTENLANDSK -> {
                    opprettUtenlandskSykmelding(
                        journalpostId = journalpostId,
                        aktorId = pasient.aktorId,
                        loggingMeta = loggingMeta,
                        sykmeldingId = sykmeldingId,
                    )
                }
                SYKMELDING_TYPE.EGENERKLARING_UTLAND -> {
                    opprettOppgaveFraEgenerklaringsskjema(
                        journalpostId = journalpostId,
                        dokumenter = dokumenter,
                        aktorId = pasient.aktorId,
                        loggingMeta = loggingMeta,
                        sykmeldingId = sykmeldingId,
                    )
                }
                else -> {
                    log.info("Utenlandsk sykmelding type $type ikke implementert")
                    return
                }
            }

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
                    dokumentInfoId = dokumentInfoId,
                    dokumenter = dokumenter.map { DokumentKafka(it.tittel, it.dokumentInfoId) },
                    type = "UTLAND",
                ),
                loggingMeta,
            )
            SYK_DIG_OPPGAVER.inc()
        }
    }

    suspend fun opprettUtenlandskSykmelding(
        journalpostId: String,
        aktorId: String,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ): OppgaveResultat? {
        return oppgaveService.opprettOppgave(
            aktoerIdPasient = aktorId,
            journalpostId = journalpostId,
            gjelderUtland = true,
            trackingId = sykmeldingId,
            loggingMeta = loggingMeta,
        )
    }

    suspend fun opprettOppgaveFraEgenerklaringsskjema(
        journalpostId: String,
        dokumenter: List<DokumentMedTittel>,
        aktorId: String,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ): OppgaveResultat? {
        val sykmeldingsdokument =
            dokumenter.firstOrNull { it.brevkode == BREVKODE_EGENERKLARING_UTENLANDSK_SYKMELDING }

        if (sykmeldingsdokument == null) {
            log.info("journalpost $journalpostId har ikke utenlandsk sykmelding includert")
            return null
        }

        if (unleash.shouldOpprettOppgaveFromEgenerklaring()) {

            log.info(
                "Oppretter utenlandsk oppgave for egenerklæring med sykmelding vedlegg med id $sykmeldingId"
            )
            return oppgaveService.opprettOppgave(
                aktoerIdPasient = aktorId,
                journalpostId = journalpostId,
                gjelderUtland = true,
                trackingId = sykmeldingId,
                loggingMeta = loggingMeta,
                type = "BEH_EL_SYM"
            )
        } else {
            log.info(
                "Oppretter ikke oppgave for legeerklæring med sykmelding vedlegg med id $sykmeldingId"
            )
        }
        return null
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
