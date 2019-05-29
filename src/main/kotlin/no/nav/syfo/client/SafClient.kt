package no.nav.syfo.client

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
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
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArgument

@KtorExperimentalAPI
class SafClient(private val endpointUrl: String, private val stsClient: StsOidcClient) {
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

    // TODO https://confluence.adeo.no/display/BOA/saf+-+REST+hentdokument and https://saf-q1.nais.preprod.local/swagger-ui.html
    suspend fun getdokument(
        journalpostId: String,
        dokumentInfoId: String,
        variantFormat: String,
        logKeys: String,
        logValues: Array<StructuredArgument>
    ): ByteArray =
                client.get<ByteArray>("$endpointUrl/rest/hentdokument/$journalpostId/$dokumentInfoId/$variantFormat") {
                    accept(ContentType.Application.Json)
                    val oidcToken = stsClient.oidcToken()
                    headers {
                        append("Authorization", "Bearer ${oidcToken.access_token}")
                    }
                }
}
