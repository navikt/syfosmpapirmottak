package no.nav.syfo.client

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.ApolloQueryCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.request.RequestHeaders
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.domain.Bruker
import no.nav.syfo.domain.JournalpostMetadata
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
class SafJournalpostClient(endpointUrl: String, private val stsClient: StsOidcClient) {

    private val apolloClient: ApolloClient = ApolloClient.builder()
            .serverUrl(endpointUrl)
            .build()

    suspend fun getJournalpostMetadata(
        journalpostId: String
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
            it.bruker()?.let { bruker ->
                JournalpostMetadata(
                    Bruker(
                        bruker.id() ?: throw IllegalStateException("Journalpost mangler brukerid"),
                        bruker.type()?.name ?: throw IllegalStateException("Journalpost mangler brukertype")
                    )
                )
            } ?: throw IllegalStateException("Journalpost mangler brukerobjekt")
        }
    }
}
