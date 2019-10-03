package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_FORDELINGSOPPGAVE
import no.nav.syfo.metrics.PAPIRSM_MAPPET_OK
import no.nav.syfo.metrics.PAPIRSM_MAPPING_FEILET
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_NORGE
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTEN_BRUKER
import no.nav.syfo.metrics.PAPIRSM_OPPGAVE
import java.time.LocalDateTime

@KtorExperimentalAPI
class SykmeldingService constructor(
    private val sakClient: SakClient,
    private val oppgaveService: OppgaveService,
    private val safDokumentClient: SafDokumentClient,
    private val norskHelsenettClient: NorskHelsenettClient,
    private val aktoerIdClient: AktoerIdClient
) {
    suspend fun behandleSykmelding(
        journalpostId: String,
        fnr: String?,
        aktorId: String?,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        loggingMeta: LoggingMeta,
        sykmeldingId: String
    ) {
        log.info("Mottatt norsk papirsykmelding, {}", fields(loggingMeta))
        PAPIRSM_MOTTATT_NORGE.inc()

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
            dokumentInfoId?.let {
                try {
                    if (datoOpprettet == null) {
                        log.error("Journalpost $journalpostId mangler datoOpprettet, {}", fields(loggingMeta))
                        throw IllegalStateException("Journalpost mangler opprettetDato")
                    }
                    val ocrFil = safDokumentClient.hentDokument(journalpostId = journalpostId, dokumentInfoId = it, msgId = sykmeldingId, loggingMeta = loggingMeta)

                    ocrFil?.let {
                        val sykmelder = hentSykmelder(ocrFil = ocrFil, sykmeldingId = sykmeldingId, loggingMeta = loggingMeta)
                        MappingService().apply {
                            mapOcrFilTilReceivedSykmelding(
                                skanningmetadata = ocrFil,
                                fnr = fnr,
                                aktorId = aktorId,
                                datoOpprettet = datoOpprettet,
                                sykmelder = sykmelder,
                                sykmeldingId = sykmeldingId,
                                loggingMeta = loggingMeta)
                            log.info("Sykmelding mappet til internt format uten feil {}", fields(loggingMeta))
                            PAPIRSM_MAPPET_OK.inc()
                        }
                    }
                } catch (e: Exception) {
                    PAPIRSM_MAPPING_FEILET.inc()
                    log.warn("Noe gikk galt ved mapping fra OCR til sykmeldingsformat: ${e.message}, {}", fields(loggingMeta))
                }
            }

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

    suspend fun hentSykmelder(ocrFil: Skanningmetadata, sykmeldingId: String, loggingMeta: LoggingMeta): Sykmelder {
        if (ocrFil.sykemeldinger.behandler == null || ocrFil.sykemeldinger.behandler.hpr == null) {
            log.error("Mangler informasjon om behandler, avbryter.. {}", fields(loggingMeta))
            throw IllegalStateException("Mangler informasjon om behandler")
        }
        val hprNummer = ocrFil.sykemeldinger.behandler.hpr.toString()

        val behandlerFraHpr = norskHelsenettClient.finnBehandler(hprNummer, sykmeldingId)

        if (behandlerFraHpr == null || behandlerFraHpr.fnr.isNullOrEmpty()) {
            log.error("Kunne ikke hente fnr for hpr {}, {}", hprNummer, fields(loggingMeta))
            throw IllegalStateException("Kunne ikke hente fnr for hpr $hprNummer")
        }

        val aktorId = aktoerIdClient.finnAktorid(behandlerFraHpr.fnr, sykmeldingId)
        if (aktorId.isNullOrEmpty()) {
            log.error("Kunne ikke hente aktørid for hpr {}, {}", hprNummer, fields(loggingMeta))
            throw IllegalStateException("Kunne ikke hente aktørid for hpr $hprNummer")
        }

        return Sykmelder(
            hprNummer = hprNummer,
            fnr = behandlerFraHpr.fnr,
            aktorId = aktorId,
            fornavn = behandlerFraHpr.fornavn,
            mellomnavn = behandlerFraHpr.mellomnavn,
            etternavn = behandlerFraHpr.etternavn
        )
    }
}
