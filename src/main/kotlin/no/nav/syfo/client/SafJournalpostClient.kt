package no.nav.syfo.client

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.ApolloQueryCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.request.RequestHeaders
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.LoggingMeta
import no.nav.syfo.domain.Bruker
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.log
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
        return journalpost?.let {
            JournalpostMetadata(
                Bruker(
                    it.bruker()?.id(),
                    it.bruker()?.type()?.name
                ), finnDokumentIdForOcr(it.dokumenter(), loggingMeta)
            )
        }
    }
}

fun finnDokumentIdForOcr(dokumentListe: List<FindJournalpostQuery.Dokumenter>?, loggingMeta: LoggingMeta): String? {
    dokumentListe?.forEach { dokument ->
        dokument.dokumentvarianter().forEach {
            if (it.variantformat().name == "ORIGINAL") {
                log.info("Fant OCR-dokument {}", loggingMeta)
                return dokument.dokumentInfoId()
            }
        }
    }
    log.warn("Fant ikke OCR-dokument {}", loggingMeta)
    return null
}
