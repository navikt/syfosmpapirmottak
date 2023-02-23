package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.log
import no.nav.syfo.metrics.ENDRET_PAPIRSM_MOTTATT
import no.nav.syfo.metrics.PAPIRSM_MOTTATT
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTEN_OCR
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions
import no.nav.syfo.utland.UtenlandskSykmeldingService

class BehandlingService(
    private val safJournalpostClient: SafJournalpostClient,
    private val sykmeldingService: SykmeldingService,
    private val utenlandskSykmeldingService: UtenlandskSykmeldingService,
    private val pdlPersonService: PdlPersonService,
) {
    suspend fun handleJournalpost(
        journalfoeringEvent: JournalfoeringHendelseRecord,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ) {
        wrapExceptions(loggingMeta) {
            val journalpostId = journalfoeringEvent.journalpostId.toString()

            if (journalfoeringEvent.temaNytt.toString() == "SYM" &&
                (journalfoeringEvent.mottaksKanal.toString() == "SKAN_NETS" || journalfoeringEvent.mottaksKanal.toString() == "SKAN_IM") &&
                (
                    journalfoeringEvent.hendelsesType.toString() == "MidlertidigJournalført" ||
                        journalfoeringEvent.hendelsesType.toString() == "JournalpostMottatt" || journalfoeringEvent.hendelsesType.toString() == "TemaEndret"
                    )
            ) {
                val requestLatency = REQUEST_TIME.startTimer()
                PAPIRSM_MOTTATT.inc()
                log.info(
                    "Mottatt papirsykmelding fra mottakskanal {}, {}",
                    journalfoeringEvent.mottaksKanal,
                    fields(loggingMeta)
                )

                if (journalfoeringEvent.hendelsesType.toString() == "TemaEndret") {
                    ENDRET_PAPIRSM_MOTTATT.inc()
                    log.info("Mottatt endret journalpost {}", fields(loggingMeta))
                }

                val journalpostMetadata = safJournalpostClient.getJournalpostMetadata(journalpostId, loggingMeta)
                    ?: throw IllegalStateException("Unable to find journalpost with id $journalpostId")

                if (journalpostMetadata.dokumentInfoId == null) {
                    PAPIRSM_MOTTATT_UTEN_OCR.inc()
                }

                log.debug("Response from saf graphql, {}", fields(loggingMeta))

                ocrDebugLog(journalpostId, journalpostMetadata, journalfoeringEvent)

                if (journalpostMetadata.jpErIkkeJournalfort) {
                    val pasient = journalpostMetadata.bruker.let {
                        if (it.id.isNullOrEmpty() || it.type.isNullOrEmpty()) {
                            log.info("Mottatt papirsykmelding der bruker mangler, {}", fields(loggingMeta))
                            null
                        } else {
                            hentBrukerIdFraJournalpost(journalpostMetadata)?.let {ident ->
                                pdlPersonService.getPdlPerson(ident, loggingMeta)
                            }
                        }
                    }

                    if (journalpostMetadata.gjelderUtland) {
                        utenlandskSykmeldingService.behandleUtenlandskSykmelding(
                            journalpostId = journalpostId,
                            dokumentInfoId = journalpostMetadata.dokumentInfoIdPdf,
                            dokumenter = journalpostMetadata.dokumenter,
                            pasient = pasient,
                            loggingMeta = loggingMeta,
                            sykmeldingId = sykmeldingId
                        )
                    } else {
                        sykmeldingService.behandleSykmelding(
                            journalpostId = journalpostId,
                            pasient = pasient,
                            datoOpprettet = journalpostMetadata.datoOpprettet,
                            dokumentInfoIdPdf = journalpostMetadata.dokumentInfoIdPdf,
                            dokumentInfoId = journalpostMetadata.dokumentInfoId,
                            temaEndret = journalfoeringEvent.hendelsesType == "TemaEndret",
                            loggingMeta = loggingMeta,
                            sykmeldingId = sykmeldingId
                        )
                    }
                } else {
                    log.info("Journalpost med id {} er allerede journalført, {}", journalpostId, fields(loggingMeta))
                }
                val currentRequestLatency = requestLatency.observeDuration()

                log.info(
                    "Finished processing took {}s, {}",
                    StructuredArguments.keyValue("latency", currentRequestLatency),
                    fields(loggingMeta)
                )
            }
        }
    }

    private fun ocrDebugLog(
        journalpostId: String,
        journalpostMetadata: JournalpostMetadata,
        journalfoeringEvent: JournalfoeringHendelseRecord,
    ) {
        // Midlertidig logging for å lettere kunne grave i sykmeldinger som ikke blir OCR-tolket riktig
        val harOcr = when (journalpostMetadata.dokumentInfoId != null) {
            true -> "har OCR"
            false -> "har ikke OCR"
        }
        val innlandUtland = when (journalpostMetadata.gjelderUtland) {
            true -> "utland"
            false -> "innland"
        }

        log.info("Papirsykmelding gjelder $innlandUtland, $harOcr, hendelsesType ${journalfoeringEvent.hendelsesType} med journalpostId: $journalpostId")
    }

    fun hentBrukerIdFraJournalpost(
        journalpost: JournalpostMetadata,
    ): String? {
        val bruker = journalpost.bruker
        val brukerId = bruker.id ?: throw IllegalStateException("Journalpost mangler brukerid, skal ikke kunne skje")
        return if (bruker.type == "AKTOERID" || bruker.type == "FNR") {
            brukerId
        } else
            return null
    }
}
