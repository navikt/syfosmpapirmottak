package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MOTTATT
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTEN_BRUKER
import no.nav.syfo.metrics.PAPIRSM_OPPGAVE
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.wrapExceptions

@KtorExperimentalAPI
class BehandlingService constructor(
    private val safJournalpostClient: SafJournalpostClient,
    private val aktoerIdClient: AktoerIdClient,
    private val sakClient: SakClient,
    private val oppgaveService: OppgaveService,
    private val fordelingsOppgaveService: FordelingsOppgaveService,
    private val safDokumentClient: SafDokumentClient
) {
    suspend fun handleJournalpost(
        journalfoeringEvent: JournalfoeringHendelseRecord,
        loggingMeta: LoggingMeta,
        sykmeldingId: String
    ) {
        wrapExceptions(loggingMeta) {
            val journalpostId = journalfoeringEvent.journalpostId.toString()

            if (journalfoeringEvent.temaNytt.toString() == "SYM" &&
                    journalfoeringEvent.mottaksKanal.toString() == "SKAN_NETS" &&
                    journalfoeringEvent.hendelsesType.toString() == "MidlertidigJournalført"
            ) {
                val requestLatency = REQUEST_TIME.startTimer()
                PAPIRSM_MOTTATT.inc()
                log.info("Mottatt papirsykmelding, {}", fields(loggingMeta))
                val journalpostMetadata = safJournalpostClient.getJournalpostMetadata(journalpostId, loggingMeta)
                        ?: throw IllegalStateException("Unable to find journalpost with id $journalpostId")

                log.debug("Response from saf graphql, {}", fields(loggingMeta))

                if (journalpostMetadata.jpErIkkeJournalfort) {
                    if (!journalpostMetadata.gjelderUtland) {
                        journalpostMetadata.dokumentInfoId?.let {
                            try {
                                safDokumentClient.hentDokument(journalpostId = journalpostId, dokumentInfoId = journalpostMetadata.dokumentInfoId, msgId = sykmeldingId, loggingMeta = loggingMeta)
                            } catch (e: Exception) {
                                log.warn("Kunne ikke hente OCR-dokument: {${e.message}}, {}", loggingMeta)
                            }
                        }
                    }

                    if (journalpostMetadata.bruker.id.isNullOrEmpty() || journalpostMetadata.bruker.type.isNullOrEmpty()) {
                        PAPIRSM_MOTTATT_UTEN_BRUKER.inc()
                        log.info("Mottatt papirsykmelding der bruker mangler, {}", fields(loggingMeta))
                        fordelingsOppgaveService.handterJournalpostUtenBruker(journalpostId, journalpostMetadata.gjelderUtland, loggingMeta, sykmeldingId)
                    } else {
                        val aktoerIdPasient = hentAktoridFraJournalpost(journalpostMetadata, sykmeldingId)
                        val fnrPasient = hentFnrFraJournalpost(journalpostMetadata, sykmeldingId)

                        if (aktoerIdPasient.isNullOrEmpty() || fnrPasient.isNullOrEmpty()) {
                            log.warn("Kunne ikke hente bruker fra aktørregister, oppretter fordelingsoppgave {}", loggingMeta)
                            fordelingsOppgaveService.handterJournalpostUtenBruker(journalpostId, journalpostMetadata.gjelderUtland, loggingMeta, sykmeldingId)
                        } else {
                            val sakId = sakClient.finnEllerOpprettSak(sykmeldingsId = sykmeldingId, aktorId = aktoerIdPasient, loggingMeta = loggingMeta)

                            val oppgave = oppgaveService.opprettOppgave(fnrPasient = fnrPasient, aktoerIdPasient = aktoerIdPasient, sakId = sakId,
                                journalpostId = journalpostId, gjelderUtland = journalpostMetadata.gjelderUtland, trackingId = sykmeldingId, loggingMeta = loggingMeta)

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
                } else {
                    log.info("Journalpost med id {} er allerede journalført, {}", journalpostId, fields(loggingMeta))
                }
                val currentRequestLatency = requestLatency.observeDuration()

                log.info("Finished processing took {}s, {}", StructuredArguments.keyValue("latency", currentRequestLatency), fields(loggingMeta))
            } else {
                log.info("Mottatt jp som ikke treffer filter, tema = ${journalfoeringEvent.temaNytt}, mottakskanal = ${journalfoeringEvent.mottaksKanal}, hendelsestype = ${journalfoeringEvent.hendelsesType}, journalpostid = $journalpostId")
            }
        }
    }

    suspend fun hentAktoridFraJournalpost(
        journalpost: JournalpostMetadata,
        sykmeldingId: String
    ): String? {
        val bruker = journalpost.bruker
        val brukerId = bruker.id ?: throw IllegalStateException("Journalpost mangler brukerid, skal ikke kunne skje")
        return if (bruker.type == "AKTOERID") {
            brukerId
        } else {
            aktoerIdClient.finnAktorid(brukerId, sykmeldingId)
        }
    }

    suspend fun hentFnrFraJournalpost(
        journalpost: JournalpostMetadata,
        sykmeldingId: String
    ): String? {
        val bruker = journalpost.bruker
        val brukerId = bruker.id ?: throw IllegalStateException("Journalpost mangler brukerid, skal ikke kunne skje")
        return if (bruker.type == "FNR") {
            brukerId
        } else {
            aktoerIdClient.finnFnr(brukerId, sykmeldingId)
        }
    }
}
