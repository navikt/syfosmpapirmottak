package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.service.OcrParserParseResponse
import no.nav.syfo.service.OcrShadowDokumentInfo

/**
 * Client for the OCR shadow-service (`sykmelding-ocr-parser`).
 *
 * The parse is asynchronous: `POST /api/parse` returns `202 { jobId }` immediately, and the result
 * is fetched by polling `GET /api/parse/{jobId}` (`202` while running, `200` with the result when
 * done). This avoids holding a single HTTP request open for the multi-minute parse, which is
 * fragile across the service mesh and was cut off after ~20s.
 */
class OcrShadowHttpClient(
    private val accessTokenClient: AzureAdV2Client,
    private val resourceId: String,
    private val client: HttpClient,
    private val ocrServiceUrl: String,
    private val pollIntervalMillis: Long = 5_000,
    private val pollTimeoutMillis: Long = 1_200_000,
) {
    suspend fun hentOcrParser(
        dokumentInfo: OcrShadowDokumentInfo,
        pdfBytes: ByteArray,
    ): List<OcrParserParseResponse> {
        val token =
            accessTokenClient.getAccessToken(resourceId)?.accessToken
                ?: throw RuntimeException("Klarte ikke hente ut accesstoken for ocrService")

        val submit =
            client.post("$ocrServiceUrl/api/parse") {
                header("Authorization", "Bearer $token")
                header("X-Document-Reference", dokumentInfo.asDocumentReference())
                header("X-Sykmelding-Id", dokumentInfo.sykmeldingId)
                contentType(ContentType.Application.OctetStream)
                setBody(pdfBytes)
            }

        when (submit.status) {
            // Async path: parse accepted, poll for the result below.
            HttpStatusCode.Accepted -> {}
            // Backwards-compatible: a synchronous server that returns the result directly.
            HttpStatusCode.OK -> return submit.body()
            else ->
                throw RuntimeException(
                    "OCR-tjenesten avviste /api/parse: ${submit.status} ${submit.bodyAsText()}"
                )
        }

        val jobId = submit.body<ParseJobAccepted>().jobId
        return pollForResult(jobId, token)
    }

    private suspend fun pollForResult(jobId: String, token: String): List<OcrParserParseResponse> {
        val deadline = System.currentTimeMillis() + pollTimeoutMillis
        while (System.currentTimeMillis() < deadline) {
            delay(pollIntervalMillis)

            val response =
                client.get("$ocrServiceUrl/api/parse/$jobId") {
                    header("Authorization", "Bearer $token")
                }

            when (response.status) {
                HttpStatusCode.OK -> return response.body()
                // Still running - keep polling.
                HttpStatusCode.Accepted -> continue
                else ->
                    throw RuntimeException(
                        "OCR-tjenesten feilet for jobId=$jobId: ${response.status} ${response.bodyAsText()}"
                    )
            }
        }
        throw RuntimeException(
            "OCR-tjenesten svarte ikke for jobId=$jobId innen ${pollTimeoutMillis}ms"
        )
    }
}

private data class ParseJobAccepted(val jobId: String)
