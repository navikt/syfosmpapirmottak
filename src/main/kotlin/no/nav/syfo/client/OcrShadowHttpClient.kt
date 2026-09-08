package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.service.OcrParserParseResponse
import no.nav.syfo.service.OcrShadowDokumentInfo

class OcrShadowHttpClient(
    private val accessTokenClient: AzureAdV2Client,
    private val resourceId: String,
    private val client: HttpClient,
    private val ocrServiceUrl: String,
) {
    suspend fun hentOcrParser(dokumentInfo: OcrShadowDokumentInfo, pdfBytes:  ByteArray): List<OcrParserParseResponse> {
        val accessToken = accessTokenClient.getAccessToken(resourceId)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for ocrService")
        }

        return client
                .post("$ocrServiceUrl/api/parse") {
                    header("Authorization", "Bearer $accessToken")
                    header("X-Document-Reference", dokumentInfo.asDocumentReference())
                    header("X-Sykmelding-Id", dokumentInfo.sykmeldingId)
                    contentType(ContentType.Application.OctetStream)
                    setBody(pdfBytes)
                }
                .body<List<OcrParserParseResponse>>()
    }
}
