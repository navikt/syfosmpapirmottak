package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.log
import no.nav.syfo.model.Behandler
import no.nav.syfo.util.LoggingMeta
import java.io.IOException

class DokArkivClient(
    private val url: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val scope: String,
    private val httpClient: HttpClient
) {
    suspend fun oppdaterOgFerdigstillJournalpost(
        journalpostId: String,
        fnr: String,
        sykmeldingId: String,
        behandler: Behandler,
        loggingMeta: LoggingMeta
    ): String {
        oppdaterJournalpost(journalpostId = journalpostId, fnr = fnr, behandler = behandler, msgId = sykmeldingId, loggingMeta = loggingMeta)
        return ferdigstillJournalpost(journalpostId = journalpostId, msgId = sykmeldingId, loggingMeta = loggingMeta)
    }

    private suspend fun ferdigstillJournalpost(
        journalpostId: String,
        msgId: String,
        loggingMeta: LoggingMeta
    ): String {
        val httpResponse: HttpResponse = httpClient.patch("$url/$journalpostId/ferdigstill") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            header("Authorization", "Bearer ${accessTokenClientV2.getAccessTokenV2(scope)}")
            header("Nav-Callid", msgId)
            setBody(
                FerdigstillJournal("9999")
            )
        }
        when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.error("Dokarkiv svarte med feilmelding ved ferdigstilling av journalpost for msgId {}, {}", msgId, fields(loggingMeta))
                throw IOException("Dokarkiv svarte med feilmelding ved ferdigstilling av journalpost for $journalpostId msgid $msgId")
            }
            HttpStatusCode.NotFound -> {
                log.error("Journalposten finnes ikke for journalpostid {}, msgId {}, {}", journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Ferdigstilling: Journalposten finnes ikke for journalpostid $journalpostId msgid $msgId")
            }
            else -> {
                log.info("ferdigstilling av journalpost ok for journalpostid {}, msgId {}, {}", journalpostId, msgId, fields(loggingMeta))
                return httpResponse.body<String>()
            }
        }
    }

    private suspend fun oppdaterJournalpost(
        journalpostId: String,
        fnr: String,
        behandler: Behandler,
        msgId: String,
        loggingMeta: LoggingMeta
    ) {
        val httpResponse: HttpResponse = httpClient.put("$url/$journalpostId") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            header("Authorization", "Bearer ${accessTokenClientV2.getAccessTokenV2(scope)}")
            header("Nav-Callid", msgId)
            setBody(
                OppdaterJournalpost(
                    avsenderMottaker = AvsenderMottaker(
                        id = hprnummerMedRiktigLengde(behandler.hpr!!),
                        navn = finnNavn(behandler)
                    ),
                    bruker = Bruker(id = fnr),
                    sak = Sak()
                )
            )
        }
        when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.error("Dokarkiv svarte med feilmelding ved oppdatering av journalpost for msgId {}, {}", msgId, fields(loggingMeta))
                throw IOException("Dokarkiv svarte med feilmelding ved oppdatering av journalpost for $journalpostId msgid $msgId")
            }
            HttpStatusCode.NotFound -> {
                log.error("Oppdatering: Journalposten finnes ikke for journalpostid {}, msgId {}, {}", journalpostId, msgId, fields(loggingMeta))
                throw RuntimeException("Oppdatering: Journalposten finnes ikke for journalpostid $journalpostId msgid $msgId")
            }
            else -> {
                log.info("Oppdatering av journalpost ok for journalpostid {}, msgId {}, {}", journalpostId, msgId, fields(loggingMeta))
            }
        }
    }

    private fun hprnummerMedRiktigLengde(hprnummer: String): String {
        if (hprnummer.length < 9) {
            return hprnummer.padStart(9, '0')
        }
        return hprnummer
    }

    private fun finnNavn(behandler: Behandler): String {
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
