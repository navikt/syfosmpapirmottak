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
 * Kjører ny OCR-tjeneste parallelt med eksisterende OCR-flyt og logger resultater til securelog
 * for sammenligning. Påvirker aldri produksjonsflyten — alle feil svelges og logges som warn.
 *
 * Fjernes etter at sammenligningstrial er ferdig og ny OCR er tatt i bruk.
 */
class OcrShadowService(
    private val safDokumentClient: SafDokumentClient,
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
            alleDokumenter
                ?.get(dokumentInfoIdPdf)
                ?.firstOrNull { it.variantFormat == DokumentVariantFormat.ARKIV }
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
                val pdfBytes =
                    safDokumentClient.getDocument(
                        journalpostId = journalpostId,
                        dokumentInfoId = dokumentInfoIdPdf,
                        dokumentVariant = pdfFilInfo,
                        loggingMeta = loggingMeta,
                        msgId = sykmeldingId,
                    )

                val token =
                    azureAdV2Client.getAccessToken(ocrServiceScope)?.accessToken
                        ?: error("Klarte ikke hente token for OCR-tjenesten")

                val nyttOcrResultat =
                    httpClient
                        .post("$ocrServiceUrl/api/parse") {
                            header("Authorization", "Bearer $token")
                            contentType(ContentType.Application.OctetStream)
                            setBody(pdfBytes)
                        }
                        .bodyAsText()

                securelog.info(
                    "ocr-shadow sykmeldingId={} journalpostId={} gammelOcr=[{}] nyOcr={}",
                    sykmeldingId,
                    journalpostId,
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
