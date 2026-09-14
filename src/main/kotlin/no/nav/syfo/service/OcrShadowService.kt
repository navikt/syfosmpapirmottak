package no.nav.syfo.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.syfo.client.DokumentVariantFormat
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.domain.DokumentFilInfo
import no.nav.syfo.log
import no.nav.syfo.securelog
import no.nav.syfo.util.LoggingMeta
import no.nav.sykmelding.api.OcrParsedSykmelding

/** Identifiserer det konkrete PDF-dokumentet en shadow-sammenligning gjelder. */
data class OcrShadowDokumentInfo(
    val sykmeldingId: String,
    val journalpostId: String,
    val dokumentInfoId: String,
    val filUuid: String,
    val filType: String,
    val filNamn: String,
)

/**
 * Kjører ny OCR-tjeneste parallelt med eksisterende OCR-flyt og logger resultater til securelog for
 * sammenligning. Påvirker aldri produksjonsflyten — alle feil svelges og logges som warn.
 *
 * Fjernes etter at sammenligningstrial er ferdig og ny OCR er tatt i bruk.
 */
class OcrShadowService(
    private val safDokumentClient: SafDokumentClient,
    private val ocrParserService: OcrParserService,
    private val ocrParserImCompareService: OcrParserImCompareService,
) {
    private val shadowScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun compareAsync(
        journalpostId: String,
        dokumentInfoIdPdf: String,
        alleDokumenter: Map<String, List<DokumentFilInfo>>?,
        gammelOcr: Skanningmetadata?,
        sykmeldingId: String,
        loggingMeta: LoggingMeta,
    ) {
        val pdfFilInfo =
            alleDokumenter?.get(dokumentInfoIdPdf)?.firstOrNull {
                it.variantFormat == DokumentVariantFormat.ARKIV
            }
                ?: run {
                    log.warn(
                        "OcrShadow: fant ikke ARKIV-variant for dokumentInfoIdPdf={} sykmeldingId={}",
                        dokumentInfoIdPdf,
                        sykmeldingId,
                    )
                    return
                }

        shadowScope.launch {
            try {
                val dokumentInfo =
                    OcrShadowDokumentInfo(
                        sykmeldingId = sykmeldingId,
                        journalpostId = journalpostId,
                        dokumentInfoId = dokumentInfoIdPdf,
                        filUuid = pdfFilInfo.filUUID,
                        filType = pdfFilInfo.filType,
                        filNamn = pdfFilInfo.filNamn,
                    )
                securelog.info(
                    "OcrShadow: sender pdf til shadow-service sykmeldingId={} journalpostId={} dokumentInfoId={} filUuid={} filNamn={}",
                    dokumentInfo.sykmeldingId,
                    dokumentInfo.journalpostId,
                    dokumentInfo.dokumentInfoId,
                    dokumentInfo.filUuid,
                    dokumentInfo.filNamn,
                )

                val pdfBytes =
                    safDokumentClient.getDocument(
                        journalpostId = journalpostId,
                        dokumentInfoId = dokumentInfoIdPdf,
                        dokumentVariant = pdfFilInfo,
                        loggingMeta = loggingMeta,
                        msgId = sykmeldingId,
                    )

                val responses = ocrParserService.parse(pdfBytes)

                val nyttOcrResultat =
                    (responses.firstOrNull() as? OcrParserParseResponse.Success)?.sykmelding
                        ?: error(
                            "OCR-tjenesten returnerte ${responses.firstOrNull()?.let { it::class.simpleName }}"
                        )

                securelog.info(
                    "ocr-shadow parser result: sykmeldingId={} \n journalpostId={} \n dokumentInfoId={} \n nyOcr={}",
                    sykmeldingId,
                    journalpostId,
                    dokumentInfoIdPdf,
                    nyttOcrResultat,
                )

                if (gammelOcr != null) {
                    ocrParserImCompareService.compare(
                        nyttOcrResultat = nyttOcrResultat,
                        ironMountainOcrResultat = gammelOcr,
                        sykmeldingId = sykmeldingId,
                        journalpostId = journalpostId,
                    )
                }
            } catch (e: Exception) {
                log.warn("OcrShadow feilet for sykmeldingId={}, hopper over", sykmeldingId, e)
            }
        }
    }
}

sealed class OcrParserParseResponse {
    data class Success(val sykmelding: OcrParsedSykmelding) : OcrParserParseResponse()

    data class Unsupported(val reason: String) : OcrParserParseResponse()

    data class Failure(val reason: String) : OcrParserParseResponse()
}
