package no.nav.syfo.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.config
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.model.Journalpost

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
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }

    // TODO https://confluence.adeo.no/pages/viewpage.action?pageId=287444683
    suspend fun getJournalpostMetadata(journalpostId: Long): Journalpost =
            client.get("$endpointUrl/rest/journalfoerinngaaende/v1/journalposter/$journalpostId") {
                accept(ContentType.Application.Json)
                val oidcToken = stsClient.oidcToken()
                headers {
                    append("Authorization", "Bearer ${oidcToken.access_token}")
                }
            }
}
