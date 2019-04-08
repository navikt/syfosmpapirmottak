package no.nav.syfo.client

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArgument
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.Journalpost

@KtorExperimentalAPI
class JournalfoerInngaaendeV1Client(private val endpointUrl: String, private val stsClient: StsOidcClient) {
    private val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }

    // TODO https://confluence.adeo.no/pages/viewpage.action?pageId=287444683
    suspend fun getJournalpostMetadata(
        journalpostId: Long,
        logKeys: String,
        logValues: Array<StructuredArgument>
    ): Journalpost =
            retry("get_journalpostMetadata") {
                client.get<HttpResponse>("$endpointUrl/rest/journalfoerinngaaende/v1/journalposter/$journalpostId") {
                    accept(ContentType.Application.Json)
                    val oidcToken = stsClient.oidcToken()
                    headers {
                        append("Authorization", "Bearer ${oidcToken.access_token}")
                    }
                }.use { it.call.response.receive<Journalpost>() }
            }
}
