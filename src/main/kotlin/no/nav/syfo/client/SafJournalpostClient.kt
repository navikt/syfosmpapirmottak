package no.nav.syfo.client

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.ApolloQueryCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.request.RequestHeaders
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.domain.Bruker
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.log
import java.time.LocalDateTime
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T> ApolloQueryCall<T>.execute() = suspendCoroutine<Response<T>> { cont ->
    enqueue(object : ApolloCall.Callback<T>() {
        override fun onResponse(response: Response<T>) {
            cont.resume(response)
        }

        override fun onFailure(e: ApolloException) {
            cont.resumeWithException(e)
        }
    })
}

@KtorExperimentalAPI
class SafJournalpostClient(private val apolloClient: ApolloClient, private val stsClient: StsOidcClient) {
    suspend fun getJournalpostMetadata(
        journalpostId: String,
        loggingMeta: LoggingMeta
    ): JournalpostMetadata? {
        val journalpost = apolloClient.query(FindJournalpostQuery.builder()
            .id(journalpostId)
            .build())
            .requestHeaders(RequestHeaders.builder()
                .addHeader("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
                .addHeader("X-Correlation-ID", journalpostId)
                .build())
            .execute()
            .data()
            ?.journalpost()
        val dokumentId: String? = finnDokumentIdForOcr(journalpost?.dokumenter(), loggingMeta)
        return journalpost?.let {
            JournalpostMetadata(
                Bruker(
                    it.bruker()?.id(),
                    it.bruker()?.type()?.name
                ),
                dokumentId,
                erIkkeJournalfort(it.journalstatus()),
                sykmeldingGjelderUtland(it.dokumenter(), dokumentId, loggingMeta),
                dateTimeStringTilLocalDateTime(it.datoOpprettet(), loggingMeta)
            )
        }
    }

    private fun erIkkeJournalfort(journalstatus: type.Journalstatus?): Boolean {
        return journalstatus?.name?.equals("MOTTATT", true) ?: false
    }
}

fun dateTimeStringTilLocalDateTime(dateTime: String?, loggingMeta: LoggingMeta): LocalDateTime? {
    dateTime?.let {
        return try {
            LocalDateTime.parse(dateTime)
        } catch (e: Exception) {
            log.error("Journalpost har ikke en gyldig datoOpprettet {}, {}", e.message, fields(loggingMeta))
            null
        }
    }
    log.error("Journalpost mangler datoOpprettet {}", fields(loggingMeta))
    return null
}

fun finnDokumentIdForOcr(dokumentListe: List<FindJournalpostQuery.Dokumenter>?, loggingMeta: LoggingMeta): String? {
    dokumentListe?.forEach { dokument ->
        dokument.dokumentvarianter().forEach {
            if (it.variantformat().name == "ORIGINAL") {
                log.info("Fant OCR-dokument {}", fields(loggingMeta))
                return dokument.dokumentInfoId()
            }
        }
    }
    log.warn("Fant ikke OCR-dokument {}", fields(loggingMeta))
    return null
}

const val BREVKODE_UTLAND: String = "900023"

fun sykmeldingGjelderUtland(dokumentListe: List<FindJournalpostQuery.Dokumenter>?, dokumentId: String?, loggingMeta: LoggingMeta): Boolean {
    if (dokumentListe == null || dokumentListe.isEmpty()) {
        log.warn("Mangler info om brevkode, antar utenlandsk sykmelding {}", fields(loggingMeta))
        return true
    }

    var brevkode: String? = null

    if (dokumentId != null) {
        val dokumenterMedRiktigId = dokumentListe.filter { it.dokumentInfoId() == dokumentId }
        if (dokumenterMedRiktigId.isNotEmpty()) {
            brevkode = dokumenterMedRiktigId[0].brevkode()
        }
    } else {
        log.info("Mangler dokumentid for OCR, prøver å finne brevkode fra resterende dokumenter {}", fields(loggingMeta))
        val inneholderUtlandBrevkode: Boolean = dokumentListe.any { dok ->  dok.brevkode() == BREVKODE_UTLAND}
        if (inneholderUtlandBrevkode) {
            brevkode = BREVKODE_UTLAND
        }
    }
    return if (brevkode == BREVKODE_UTLAND) {
        log.info("Sykmelding gjelder utenlandsk sykmelding, brevkode: {}, {}", brevkode, fields(loggingMeta))
        true
    } else {
        log.info("Sykmelding gjelder innenlands-sykmelding, brevkode: {}, {}", brevkode, fields(loggingMeta))
        false
    }
}
