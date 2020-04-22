package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import java.io.IOException
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.Behandler
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class DokArkivClient(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun oppdaterOgFerdigstillJournalpost(
        journalpostId: String,
        fnr: String,
        sykmeldingId: String,
        behandler: Behandler,
        loggingMeta: LoggingMeta
    ): String? {
        oppdaterJournalpost(journalpostId = journalpostId, fnr = fnr, behandler = behandler, msgId = sykmeldingId, loggingMeta = loggingMeta)
        return ferdigstillJournalpost(journalpostId = journalpostId, msgId = sykmeldingId, loggingMeta = loggingMeta)
    }

    suspend fun ferdigstillJournalpost(
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
            log.error("Dokarkiv svarte med feilmelding ved ferdigstilling av journalpost for msgId {}, {}", msgId, fields(loggingMeta))
            throw IOException("Dokarkiv svarte med feilmelding ved ferdigstilling av journalpost for $journalpostId msgid $msgId")
        }
        when (httpResponse.status) {
            HttpStatusCode.NotFound -> {
                log.error("Journalposten finnes ikke for journalpostid {}, msgId {}, {}", journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Ferdigstilling: Journalposten finnes ikke for journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.BadRequest -> {
                log.error("Fikk http status {} for journalpostid {}, msgId {}, {}", HttpStatusCode.BadRequest.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk BadRequest ved ferdigstilling av journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Fikk http status {} for journalpostid {}, msgId {}, {}", HttpStatusCode.Unauthorized.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk 401 ved ferdigstilling av journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.Forbidden -> {
                log.error("Fikk http status {} for journalpostid {}, msgId {}, {}", HttpStatusCode.Forbidden.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk 403 ved ferdigstilling av journalpostid $journalpostId msgid $msgId")
            }
            else -> {
                log.info("ferdigstilling av journalpost ok for journalpostid {}, msgId {}, http status {} , {}", journalpostId, msgId, httpResponse.status.value, fields(loggingMeta))
                httpResponse.call.response.receive<String>()
            }
        }
    }

    suspend fun oppdaterJournalpost(
        journalpostId: String,
        fnr: String,
        behandler: Behandler,
        msgId: String,
        loggingMeta: LoggingMeta
    ) = retry("oppdater_journalpost") {
        val httpResponse = httpClient.put<HttpStatement>("$url/$journalpostId") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("Nav-Callid", msgId)
            body = OppdaterJournalpost(
                avsenderMottaker = AvsenderMottaker(
                    id = hprnummerMedRiktigLengde(behandler.hpr!!),
                    navn = finnNavn(behandler)
                ),
                bruker = Bruker(id = fnr),
                sak = Sak()
            )
        }.execute()
        if (httpResponse.status == HttpStatusCode.InternalServerError) {
            log.error("Dokarkiv svarte med feilmelding ved oppdatering av journalpost for msgId {}, {}", msgId, fields(loggingMeta))
            throw IOException("Dokarkiv svarte med feilmelding ved oppdatering av journalpost for $journalpostId msgid $msgId")
        }
        when (httpResponse.status) {
            HttpStatusCode.NotFound -> {
                log.error("Oppdatering: Journalposten finnes ikke for journalpostid {}, msgId {}, {}", journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Oppdatering: Journalposten finnes ikke for journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.BadRequest -> {
                log.error("Fikk http status {} ved oppdatering av journalpostid {}, msgId {}, {}", HttpStatusCode.BadRequest.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk BadRequest ved oppdatering av journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.Unauthorized -> {
                log.error("Fikk http status {} ved oppdatering av journalpostid {}, msgId {}, {}", HttpStatusCode.Unauthorized.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk 401 ved oppdatering av journalpostid $journalpostId msgid $msgId")
            }
            HttpStatusCode.Forbidden -> {
                log.error("Fikk http status {} ved oppdatering av journalpostid {}, msgId {}, {}", HttpStatusCode.Forbidden.value, journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Fikk 403 ved oppdatering av journalpostid $journalpostId msgid $msgId")
            }
            else -> {
                log.info("Oppdatering av journalpost ok for journalpostid {}, msgId {}, http status {} , {}", journalpostId, msgId, httpResponse.status.value, fields(loggingMeta))
            }
        }
    }

    fun hprnummerMedRiktigLengde(hprnummer: String): String {
        if (hprnummer.length < 9) {
            return hprnummer.padStart(9, '0')
        }
        return hprnummer
    }

    fun finnNavn(behandler: Behandler): String {
        return "${behandler.fornavn} ${behandler.etternavn}"
    }

    data class FerdigstillJournal(
        val journalfoerendeEnhet: String
    )

    data class OppdaterJournalpost(
        val tema: String = "SYM",
        val avsenderMottaker: AvsenderMottaker,
        val bruker: Bruker,
        val sak: Sak
    )

    data class AvsenderMottaker(
        val id: String,
        val idType: String = "HPRNR",
        val navn: String
    )

    data class Bruker(
        val id: String,
        val idType: String = "FNR"
    )

    data class Sak(
        val sakstype: String = "GENERELL_SAK"
    )
}
