package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult

class RegelClient(
    private val endpointUrl: String,
    private val accessTokenClient: AzureAdV2Client,
    private val resourceId: String,
    private val client: HttpClient,
) {
    suspend fun valider(sykmelding: ReceivedSykmelding, msgId: String): ValidationResult {
        val accessToken = accessTokenClient.getAccessToken(resourceId)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for syfosmpapirregler")
        }

        return client.post("$endpointUrl/api/v2/rules/validate") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer ${accessToken.accessToken}")
                append("Nav-CallId", msgId)
            }
            setBody(sykmelding)
        }.body<ValidationResult>()
    }
}
