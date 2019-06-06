package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.OppgaveResponse
import no.nav.syfo.model.OpprettOppgave

@KtorExperimentalAPI
class OppgaveClient constructor(val url: String, val oidcClient: StsOidcClient) {
    private val client: HttpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    suspend fun createOppgave(createOppgave: OpprettOppgave, msgId: String): OppgaveResponse = retry("create_oppgave") {
        client.post<OppgaveResponse>(url) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            this.header("Authorization", "Bearer ${oidcToken.access_token}")
            this.header("X-Correlation-ID", msgId)
            body = createOppgave
        }
    }
}
