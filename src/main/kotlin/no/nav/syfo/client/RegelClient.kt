package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.syfo.application.azuread.v2.AzureAdV2Client
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult

class RegelClient(
    private val endpointUrl: String,
    private val azureAdV2Client: AzureAdV2Client,
    private val resourceId: String,
    private val client: HttpClient
) {
    suspend fun valider(sykmelding: ReceivedSykmelding, msgId: String): ValidationResult {
        return client.post("$endpointUrl/api/v2/rules/validate") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            val accessToken = azureAdV2Client.getAccessToken(resourceId)
            if (accessToken?.accessToken == null) {
                throw RuntimeException("Klarte ikke hente ut accesstoken for smgcp-proxy")
            }
            headers {
                append("Authorization", "Bearer ${accessToken.accessToken}")
                append("Nav-CallId", msgId)
            }
            setBody(sykmelding)
        }.body<ValidationResult>()
    }
}
