package no.nav.syfo.service

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.client.DokumentVariantFormat
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.domain.DokumentFilInfo
import no.nav.syfo.log
import no.nav.syfo.securelog
import no.nav.syfo.util.LoggingMeta

/**
 * Identifiserer det konkrete PDF-dokumentet en shadow-sammenligning gjelder.
 */
data class OcrShadowDokumentInfo(
    val sykmeldingId: String,
    val journalpostId: String,
    val dokumentInfoId: String,
    val filUuid: String,
    val filType: String,
    val filNamn: String,
) {
    /**
     * Opaque correlation token for shadow-service's `X-Document-Reference` header (see
     * navikt/sykmelding-ocr-parser OcrRoutes.kt). Built to be identical to the bucket blob name
     * BucketUploadService.saveToBucket() generates (`${journalpostId}_${dokumentInfoId}_${filUUID}.${filType}`)
     * so it can be pasted straight into the bucket to find the document — never the
     * human-readable filNamn, which must stay in securelog only.
     */
    fun asDocumentReference(): String = "${journalpostId}_${dokumentInfoId}_${filUuid}.${filType.lowercase()}"
}

/**
 * Kjører ny OCR-tjeneste parallelt med eksisterende OCR-flyt og logger resultater til securelog for
 * sammenligning. Påvirker aldri produksjonsflyten — alle feil svelges og logges som warn.
 *
 * Fjernes etter at sammenligningstrial er ferdig og ny OCR er tatt i bruk.
 */
class OcrShadowService(
    private val safDokumentClient: SafDokumentClient,
    // Dedicated HttpClient with a 90s timeout — the new OCR service can be slower than our
    // other dependencies, so it must not share the shorter timeout used elsewhere.
    private val httpClient: HttpClient,
    private val ocrServiceUrl: String,
    private val ocrServiceScope: String,
    private val azureAdV2Client: AzureAdV2Client,
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

                val token: String =
                    azureAdV2Client.getAccessToken(ocrServiceScope)?.accessToken
                        ?: run {
                            log.warn(
                                "OcrShadow: klarte ikke hente token for sykmeldingId={}, hopper over",
                                sykmeldingId
                            )
                            return@launch
                        }

                val nyttOcrResultat =
                    httpClient
                        .post("$ocrServiceUrl/api/parse") {
                            header("Authorization", "Bearer $token")
                            header("X-Document-Reference", dokumentInfo.asDocumentReference())
                            contentType(ContentType.Application.OctetStream)
                            setBody(pdfBytes)
                        }
                        .bodyAsText()

                securelog.info(
                    "ocr-shadow sykmeldingId={} journalpostId={} dokumentInfoId={} gammelOcr=[{}] nyOcr={}",
                    sykmeldingId,
                    journalpostId,
                    dokumentInfoIdPdf,
                    gammelOcr,
                    nyttOcrResultat,
                )
            } catch (e: Exception) {
                log.warn(
                    "OcrShadow feilet for sykmeldingId={}, hopper over",
                    sykmeldingId,
                    e,
                )
            }
        }
    }
}
