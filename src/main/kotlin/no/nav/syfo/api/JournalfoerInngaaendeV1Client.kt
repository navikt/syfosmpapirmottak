package no.nav.syfo.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.config
import io.ktor.client.features.auth.basic.BasicAuth
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI

@KtorExperimentalAPI
class JournalfoerInngaaendeV1Client(private val endpointUrl: String, private val stsClient: StsOidcClient) {
    private val client = HttpClient(CIO.config {
        maxConnectionsCount = 1000 // Maximum number of socket connections.
        endpoint.apply {
            maxConnectionsPerRoute = 100
            pipelineMaxSize = 20
            keepAliveTime = 5000
            connectTimeout = 5000
            connectRetryAttempts = 5
        }
    }) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        install(BasicAuth) {
            this.username = username
            this.password = password
        }
    }

    // TODO https://confluence.adeo.no/pages/viewpage.action?pageId=287444683
    suspend fun getAktoerIds(personNumbers: List<String>, trackingId: String, username: String): Map<String, String> =
            client.get("$endpointUrl/identer") {
                accept(ContentType.Application.Json)
                val oidcToken = stsClient.oidcToken()
                headers {
                    append("Authorization", "Bearer ${oidcToken.access_token}")
                    append("Nav-Consumer-Id", username)
                    append("Nav-Call-Id", trackingId)
                    append("Nav-Personidenter", personNumbers.joinToString(","))
                }
                parameter("gjeldende", "true")
                parameter("identgruppe", "AktoerId")
            }
}
