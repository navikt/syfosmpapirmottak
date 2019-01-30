package no.nav.syfo.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.config
import io.ktor.client.features.auth.basic.BasicAuth
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.ApplicationConfig
import no.nav.syfo.VaultCredentials

@KtorExperimentalAPI
fun createHttpClient(credentials: VaultCredentials) = HttpClient(CIO.config {
    maxConnectionsCount = 1000 // Maximum number of socket connections.
    endpoint.apply {
        maxConnectionsPerRoute = 100
        pipelineMaxSize = 20
        keepAliveTime = 5000
        connectTimeout = 5000
        connectRetryAttempts = 5
    }
}) {
    install(BasicAuth) {
        username = credentials.serviceuserUsername
        password = credentials.serviceuserPassword
    }
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
}

suspend fun HttpClient.executeRuleValidation(config: ApplicationConfig, payload: String): ValidationResult = post {
    body = payload

    url {
        host = config.syfoSmRegelerApiURL
        path("v1", "rules", "validate")
    }
}

data class ValidationResult(
    val status: Status,
    val ruleHits: List<RuleInfo>
)

data class RuleInfo(
    val ruleMessage: String
)

enum class Status {
    OK,
    MANUAL_PROCESSING,
    INVALID
}
