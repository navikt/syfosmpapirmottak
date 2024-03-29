package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

class TssException(message: String) : Exception(message)

class SmtssClient(
    private val endpointUrl: String,
    private val accessTokenClientV2: AzureAdV2Client,
    private val resourceId: String,
    private val httpClient: HttpClient,
) {
    suspend fun findBestTssInfotrygdId(
        samhandlerFnr: String,
        samhandlerOrgName: String,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ): String? {
        val accessToken = accessTokenClientV2.getAccessToken(resourceId)
        if (accessToken?.accessToken == null) {
            throw TssException("Klarte ikke hente ut accesstoken for smtsd")
        }

        val httpResponse =
            httpClient.get("$endpointUrl/api/v1/samhandler/infotrygd") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header("samhandlerFnr", samhandlerFnr)
                header("samhandlerOrgName", samhandlerOrgName)
                header("Authorization", "Bearer ${accessToken.accessToken}")
                header("requestId", sykmeldingId)
            }
        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.body<TSSident>().tssid
            }
            HttpStatusCode.NotFound -> {
                log.info(
                    "smtss responded with {} for {}",
                    httpResponse.status,
                    StructuredArguments.fields(loggingMeta),
                )
                null
            }
            else -> {
                log.error("Error getting TSS-id ${httpResponse.status}")
                throw TssException("Error getting TSS-id")
            }
        }
    }
}

data class TSSident(
    val tssid: String,
)
