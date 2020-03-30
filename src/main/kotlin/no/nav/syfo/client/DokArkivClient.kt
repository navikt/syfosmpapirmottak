package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import java.io.IOException
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class DokArkivClient(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun ferdigStillJournalpost(
        journalpostId: String,
        msgId: String,
        loggingMeta: LoggingMeta
    ): String? = retry("ferdigstill_journalpost") {
        val httpResponse = httpClient.patch<HttpStatement>("$url/$journalpostId/ferdigstill") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("Nav-Callid", msgId)
            body = FerdigstillJournal("9999")
        }.execute()
        if (httpResponse.status == HttpStatusCode.InternalServerError) {
            log.error("Dokakriv svarte med feilmelding ved ferdigstilling av journalpost for msgId {}, {}", msgId, fields(loggingMeta))
            throw IOException("Saf svarte med feilmelding ved ferdigstilling av journalpost for $journalpostId msgid $msgId")
        }
        when (httpResponse.status) {
            HttpStatusCode.NotFound -> {
                log.error("Journalposten finnes ikke for journalpostid {}, msgId {}, {}", journalpostId, msgId, fields(loggingMeta))
                null
            }
            HttpStatusCode.BadRequest -> {
                log.error("Fikk http status {} for journalpostid {}, msgId {}, {}", HttpStatusCode.BadRequest.value, journalpostId, msgId, fields(loggingMeta))
                null
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Fikk http status {} for journalpostid {}, msgId {}, {}", HttpStatusCode.Unauthorized.value, journalpostId, msgId, fields(loggingMeta))
                null
            }
            HttpStatusCode.PaymentRequired -> {
                log.error("Fikk http status {} for journalpostid {}, msgId {}, {}", HttpStatusCode.PaymentRequired.value, journalpostId, msgId, fields(loggingMeta))
                null
            }
            HttpStatusCode.Forbidden -> {
                log.error("Fikk http status {} for journalpostid {}, msgId {}, {}", HttpStatusCode.Forbidden.value, journalpostId, msgId, fields(loggingMeta))
                null
            }
            else -> {
                log.info("ferdigstilling av journalpost ok for journalpostid {}, msgId {}, http status {} , {}", journalpostId, msgId, httpResponse.status.value, fields(loggingMeta))
                httpResponse.call.response.receive<String>()
            }
        }
    }

    data class FerdigstillJournal(
        val journalfoerendeEnhet: String
    )
}
