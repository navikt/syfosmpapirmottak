package no.nav.syfo.client

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
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
    }

    // TODO https://confluence.adeo.no/pages/viewpage.action?pageId=287444683
    suspend fun getJournalpostMetadata(
        journalpostId: Long
    ): Journalpost =
            retry("get_journalpostMetadata") {
                client.get<Journalpost>("$endpointUrl/rest/journalfoerinngaaende/v1/journalposter/$journalpostId") {
                    accept(ContentType.Application.Json)
                    header("X-Correlation-ID", journalpostId)
                    header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
                }
            }
}
