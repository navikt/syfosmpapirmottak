package no.nav.syfo.client

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.auth.basic.BasicAuth
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Deferred
import net.logstash.logback.argument.StructuredArgument
import no.nav.syfo.VaultCredentials
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.retryAsync

@KtorExperimentalAPI
class SyfoSykemeldingRuleClient(private val endpointUrl: String, credentials: VaultCredentials) {
    private val client = HttpClient(CIO) {
        install(BasicAuth) {
            username = credentials.serviceuserUsername
            password = credentials.serviceuserPassword
        }
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
        }
    }

    suspend fun executeRuleValidation(
        payload: ReceivedSykmelding,
        logKeys: String,
        logValues: Array<StructuredArgument>
    ): Deferred<ValidationResult> =
            client.retryAsync("syfosmregler_validate", logKeys, logValues) {
                client.post<ValidationResult>("$endpointUrl/v1/rules/validate") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                body = payload
                }
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
