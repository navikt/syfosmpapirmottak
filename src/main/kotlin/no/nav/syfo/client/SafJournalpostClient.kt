package no.nav.syfo.client

import FindJournalpostQuery
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.ApolloQueryCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.request.RequestHeaders
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.azuread.v2.AzureAdV2Client
import no.nav.syfo.domain.Bruker
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
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

class SafJournalpostClient(
    private val apolloClient: ApolloClient,
    private val azureAdV2Client: AzureAdV2Client,
    private val scope: String
) {
    suspend fun getJournalpostMetadata(
        journalpostId: String,
        loggingMeta: LoggingMeta
    ): JournalpostMetadata? {

        val accessToken = azureAdV2Client.getAccessToken(scope)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for SafJournalpostClient")
        }

        val journalpost = apolloClient.query(
            FindJournalpostQuery.builder()
                .id(journalpostId)
                .build()
        ).toBuilder()
            .requestHeaders(
                RequestHeaders.builder()
                    .addHeader("Authorization", "Bearer ${accessToken.accessToken}")
                    .addHeader("X-Correlation-ID", journalpostId)
                    .build()
            ).build()
            .execute()
            .data
            ?.journalpost()
        val dokumentId: String? = finnDokumentIdForOcr(journalpost?.dokumenter(), loggingMeta)
        return journalpost?.let {
            JournalpostMetadata(
                bruker = Bruker(
                    it.bruker()?.id(),
                    it.bruker()?.type()?.name
                ),
                dokumentInfoId = dokumentId,
                jpErIkkeJournalfort = erIkkeJournalfort(it.journalstatus()),
                gjelderUtland = sykmeldingGjelderUtland(it.dokumenter(), dokumentId, loggingMeta),
                datoOpprettet = dateTimeStringTilLocalDateTime(it.datoOpprettet(), loggingMeta),
                dokumentInfoIdPdf = finnDokumentIdForPdf(journalpost.dokumenter(), loggingMeta)
            )
        }
    }

    private fun erIkkeJournalfort(journalstatus: type.Journalstatus?): Boolean {
        return journalstatus?.name?.let {
            it.equals("MOTTATT", true) || it.equals("FEILREGISTRERT", true)
        } ?: false
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

fun finnDokumentIdForPdf(dokumentListe: List<FindJournalpostQuery.Dokumenter>?, loggingMeta: LoggingMeta): String {
    dokumentListe?.forEach { dokument ->
        dokument.dokumentvarianter().forEach {
            if (it.variantformat().name == "ARKIV") {
                return dokument.dokumentInfoId()
            }
        }
    }
    log.error("Fant ikke PDF-dokument {}", fields(loggingMeta))
    throw RuntimeException("Har mottatt papirsykmelding uten PDF, journalpostId: ${loggingMeta.journalpostId}")
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
        val inneholderUtlandBrevkode: Boolean = dokumentListe.any { dok -> dok.brevkode() == BREVKODE_UTLAND }
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
