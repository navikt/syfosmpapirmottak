package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

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
            throw RuntimeException("Klarte ikke hente ut accesstoken for Oppgave")
        }
     
        val httpResponse = httpClient.get("$endpointUrl/api/v1/samhandler/infotrygd") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            parameter("samhandlerFnr", samhandlerFnr)
            parameter("samhandlerOrgName", samhandlerOrgName)
            header("Authorization", "Bearer ${accessToken.accessToken}")
            header("requestId", sykmeldingId)
        }
        return if (httpResponse.status == HttpStatusCode.OK) {
            val tssid = httpResponse.body<TSSident>().tssid
            tssid
        } else {
            log.info(
                "smtss responded with an error code {} for {}",
                httpResponse.status,
                StructuredArguments.fields(loggingMeta),
            )
            null
        }
    }
}

data class TSSident(
    val tssid: String,
)
