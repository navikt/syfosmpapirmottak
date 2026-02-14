package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MOTTATT
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.securelog
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.utland.UtenlandskSykmeldingService

const val GAMMEL_BREVKODE_UTLAND: String = "900023"
const val BREVKODE_UTLAND: String = "NAV 08-07.04 U"
const val BREVKODE_NORSK: String = "NAV 08-07.04"
const val BREVKODE_EGENERKLARING_FOR_UTENLENDSK_SYKMELDING: String = "NAV 08-09.06"
const val BREVKODE_EGENERKLARING_UTENLANDSK_SYKMELDING: String = "CH"

enum class SYKMELDING_TYPE {
    NORSK,
    EGENERKLARING_UTLAND,
    UTENLANDSK,
}

class BehandlingService(
    private val safJournalpostClient: SafJournalpostClient,
    private val sykmeldingService: SykmeldingService,
    private val utenlandskSykmeldingService: UtenlandskSykmeldingService,
    private val pdlPersonService: PdlPersonService,
) {

    private val hendelsesTyper =
        setOf<String>("MidlertidigJournalført", "JournalpostMottatt", "TemaEndret")

    private val temaer = setOf<String>("SYK", "SYM")

    private val mottaksKanaler = setOf<String>("SKAN_NETS", "NAV_NO", "SKAN_NETS")

    private val sykmeldingBrevkodeOgType =
        mapOf<String, SYKMELDING_TYPE>(
            "NAV 08-07.04 A" to SYKMELDING_TYPE.NORSK,
            "NAV 08-07.04 D" to SYKMELDING_TYPE.NORSK,
            "NAV 08-07.04 L" to SYKMELDING_TYPE.NORSK,
            "NAV 08-07.04 P" to SYKMELDING_TYPE.NORSK,
            "NAV 08-07.04 Q" to SYKMELDING_TYPE.NORSK,
            "NAV 08-07.04 R" to SYKMELDING_TYPE.NORSK,
            "NAV 08-07.04L" to SYKMELDING_TYPE.NORSK,
            "NAV 08-07.04P" to SYKMELDING_TYPE.NORSK,
            "NAV 08-07.04Q" to SYKMELDING_TYPE.NORSK,
            "NAV 08-07.04R" to SYKMELDING_TYPE.NORSK,
            "NAV 08-07.04 U" to SYKMELDING_TYPE.UTENLANDSK,
            "NAV 08-09.06" to SYKMELDING_TYPE.EGENERKLARING_UTLAND,
            "NAVe 08-09.08" to SYKMELDING_TYPE.EGENERKLARING_UTLAND
        )

    suspend fun handleJournalpost(
        journalfoeringEvent: JournalfoeringHendelseRecord,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ) {
        val journalpostId = journalfoeringEvent.journalpostId.toString()
        val temaNytt = journalfoeringEvent.temaNytt.toString()
        val mottaksKanal = journalfoeringEvent.mottaksKanal.toString()
        val hendelsesType = journalfoeringEvent.hendelsesType.toString()

        log.info(
            "Mottatt dokument med tema: $temaNytt, mottaksKanal: $mottaksKanal, hendelsestype: $hendelsesType and journalpostId $journalpostId",
            fields(loggingMeta)
        )

        if (
            !(temaer.contains(temaNytt) &&
                hendelsesTyper.contains(mottaksKanal) &&
                mottaksKanaler.contains(hendelsesType))
        ) {
            log.info("dokument er ikke sykmelding: $journalpostId")
            return
        }

        PAPIRSM_MOTTATT.inc()

        val journalpostMetadata =
            safJournalpostClient.getJournalpostMetadata(
                journalpostId,
                loggingMeta,
            )
                ?: throw IllegalStateException(
                    "Unable to find journalpost with id $journalpostId",
                )

        securelog.info(
            "Response from saf graphql, ${objectMapper.writeValueAsString(journalpostMetadata)}",
            fields(loggingMeta)
        )

        if (!journalpostMetadata.jpErIkkeJournalfort) {
            log.info("Journalpost $journalpostId er journalfort")
            return
        }

        val pasient =
            journalpostMetadata.bruker.let {
                if (it.id.isNullOrEmpty() || it.type.isNullOrEmpty()) {
                    log.info(
                        "Mottatt papirsykmelding der bruker mangler, {}",
                        fields(loggingMeta),
                    )
                    null
                } else {
                    hentBrukerIdFraJournalpost(journalpostMetadata)?.let { ident ->
                        pdlPersonService.getPdlPerson(ident, loggingMeta)
                    }
                }
            }

        val dokumenter = journalpostMetadata.journalpost.dokumenter

        val brevkode = dokumenter?.first()?.brevkode
        val sykmeldingsType = sykmeldingBrevkodeOgType[brevkode]

        if (
            sykmeldingsType == SYKMELDING_TYPE.UTENLANDSK ||
                sykmeldingsType == SYKMELDING_TYPE.EGENERKLARING_UTLAND
        ) {
            utenlandskSykmeldingService.behandleUtenlandskSykmelding(
                journalpostId = journalpostId,
                dokumentInfoId = journalpostMetadata.dokumentInfoIdPdf,
                dokumenter = journalpostMetadata.dokumenter,
                pasient = pasient,
                loggingMeta = loggingMeta,
                sykmeldingId = sykmeldingId,
            )
        } else if (sykmeldingsType == SYKMELDING_TYPE.NORSK) {
            sykmeldingService.behandleSykmelding(
                journalpostId = journalpostId,
                pasient = pasient,
                datoOpprettet = journalpostMetadata.datoOpprettet,
                dokumentInfoIdPdf = journalpostMetadata.dokumentInfoIdPdf,
                dokumentInfoId = journalpostMetadata.dokumentInfoId,
                temaEndret = journalfoeringEvent.hendelsesType == "TemaEndret",
                loggingMeta = loggingMeta,
                sykmeldingId = sykmeldingId,
            )
        } else {
            log.info(
                "ugylidg sykmelidngstype for brevkoder: $brevkode, for journalpostId $journalpostId"
            )
        }
    }

    private fun hentBrukerIdFraJournalpost(
        journalpost: JournalpostMetadata,
    ): String? {
        val bruker = journalpost.bruker
        val brukerId =
            bruker.id
                ?: throw IllegalStateException("Journalpost mangler brukerid, skal ikke kunne skje")
        return if (bruker.type == "AKTOERID" || bruker.type == "FNR") {
            brukerId
        } else {
            return null
        }
    }
}
