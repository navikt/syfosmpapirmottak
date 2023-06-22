package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.LocalDateTime
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

class SafJournalpostClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val accessTokenClientV2: AzureAdV2Client,
    private val scope: String,
) {
    suspend fun getJournalpostMetadata(
        journalpostId: String,
        findJournalpostGraphQlQuery: String,
        loggingMeta: LoggingMeta,
    ): JournalpostMetadata? {
        val accessToken = accessTokenClientV2.getAccessToken(scope)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for Saf")
        }

        val findJournalpostRequest =
            FindJournalpostRequest(
                query = findJournalpostGraphQlQuery,
                variables =
                    Variables(
                        journalpostId = journalpostId,
                    ),
            )

        val findJournalpostResponse =
            httpClient
                .post(basePath) {
                    setBody(findJournalpostRequest)
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${accessToken.accessToken}")
                        append("X-Correlation-ID", journalpostId)
                        append(HttpHeaders.ContentType, "application/json")
                    }
                }
                .body<GraphQLResponse<FindJournalpostResponse>?>()

        if (findJournalpostResponse == null) {
            log.error("Kall til SAF feilet for $journalpostId")
            return null
        }
        if (findJournalpostResponse.errors != null) {
            findJournalpostResponse.errors.forEach { log.error("Saf kastet error: {} ", it) }
            return null
        }

        if (findJournalpostResponse.data.journalpost.journalstatus == null) {
            log.error("Klarte ikke hente data fra SAF {}", journalpostId)
            return null
        }

        val journalpost = findJournalpostResponse.data.journalpost

        val dokumentId: String? = finnDokumentIdForOcr(journalpost.dokumenter, loggingMeta)
        return journalpost.let {
            val dokumenter = finnDokumentIdForPdf(journalpost.dokumenter, loggingMeta)

            JournalpostMetadata(
                bruker =
                    no.nav.syfo.domain.Bruker(
                        it.bruker.id,
                        it.bruker.type?.name,
                    ),
                dokumentInfoId = dokumentId,
                jpErIkkeJournalfort = erIkkeJournalfort(it.journalstatus),
                gjelderUtland = sykmeldingGjelderUtland(it.dokumenter, dokumentId, loggingMeta),
                datoOpprettet = dateTimeStringTilLocalDateTime(it.datoOpprettet, loggingMeta),
                dokumentInfoIdPdf = dokumenter.first().dokumentInfoId,
                dokumenter = dokumenter,
            )
        }
    }

    private fun erIkkeJournalfort(journalstatus: Journalstatus?): Boolean {
        return journalstatus?.name?.let {
            it.equals("MOTTATT", true) || it.equals("FEILREGISTRERT", true)
        }
            ?: false
    }
}

data class GraphQLResponse<T>(
    val data: T,
    val errors: List<ResponseError>?,
)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?,
)

data class ErrorLocation(
    val line: String?,
    val column: String?,
)

data class ErrorExtension(
    val code: String?,
    val classification: String?,
)

fun dateTimeStringTilLocalDateTime(dateTime: String?, loggingMeta: LoggingMeta): LocalDateTime? {
    dateTime?.let {
        return try {
            LocalDateTime.parse(dateTime)
        } catch (e: Exception) {
            log.error(
                "Journalpost har ikke en gyldig datoOpprettet {}, {}",
                e.message,
                fields(loggingMeta),
            )
            null
        }
    }
    log.error("Journalpost mangler datoOpprettet {}", fields(loggingMeta))
    return null
}

fun finnDokumentIdForOcr(dokumentListe: List<Dokument>?, loggingMeta: LoggingMeta): String? {
    dokumentListe?.forEach { dokument ->
        dokument.dokumentvarianter.forEach {
            if (it.variantformat.name == "ORIGINAL") {
                log.info("Fant OCR-dokument {}", fields(loggingMeta))
                return dokument.dokumentInfoId
            }
        }
    }
    log.warn("Fant ikke OCR-dokument {}", fields(loggingMeta))
    return null
}

data class DokumentMedTittel(
    val tittel: String,
    val dokumentInfoId: String,
)

fun finnDokumentIdForPdf(
    dokumentListe: List<Dokument>?,
    loggingMeta: LoggingMeta,
): List<DokumentMedTittel> {
    val dokumenter =
        dokumentListe
            ?.filter {
                it.dokumentvarianter.any { variant -> variant.variantformat.name == "ARKIV" }
            }
            ?.map { dokument ->
                DokumentMedTittel(
                    tittel = dokument.tittel ?: "Dokument uten tittel",
                    dokumentInfoId = dokument.dokumentInfoId,
                )
            }

    if (dokumenter.isNullOrEmpty()) {
        log.error("Fant ikke PDF-dokument {}", fields(loggingMeta))
        throw RuntimeException(
            "Har mottatt papirsykmelding uten PDF, journalpostId: ${loggingMeta.journalpostId}",
        )
    }

    return dokumenter
}

const val GAMMEL_BREVKODE_UTLAND: String = "900023"
const val BREVKODE_UTLAND: String = "NAV 08-07.04 U"
const val BREVKODE_NORSK: String = "NAV 08-07.04"

fun sykmeldingGjelderUtland(
    dokumentListe: List<Dokument>?,
    dokumentId: String?,
    loggingMeta: LoggingMeta,
): Boolean {
    if (dokumentListe.isNullOrEmpty()) {
        log.warn("Mangler info om brevkode, antar utenlandsk sykmelding {}", fields(loggingMeta))
        return true
    }

    var brevkode: String? = null

    if (dokumentId != null) {
        val dokumenterMedRiktigId = dokumentListe.filter { it.dokumentInfoId == dokumentId }
        if (dokumenterMedRiktigId.isNotEmpty()) {
            brevkode = dokumenterMedRiktigId[0].brevkode
            if (brevkode != BREVKODE_NORSK) {
                log.warn("Fant OCR-fil med uventet brevkode: $brevkode {}", fields(loggingMeta))
            }
        }
    } else {
        log.info(
            "Mangler dokumentid for OCR, prøver å finne brevkode fra resterende dokumenter {}",
            fields(loggingMeta),
        )
        val inneholderUtlandBrevkode: Boolean =
            dokumentListe.any { dok -> dok.brevkode == BREVKODE_UTLAND }
        if (inneholderUtlandBrevkode) {
            brevkode = BREVKODE_UTLAND
        } else {
            val inneholderGammelUtlandBrevkode: Boolean =
                dokumentListe.any { dok -> dok.brevkode == GAMMEL_BREVKODE_UTLAND }
            if (inneholderGammelUtlandBrevkode) {
                brevkode = GAMMEL_BREVKODE_UTLAND
            }
        }
    }
    return if (brevkode == BREVKODE_UTLAND || brevkode == GAMMEL_BREVKODE_UTLAND) {
        log.info(
            "Sykmelding gjelder utenlandsk sykmelding, brevkode: {}, {}",
            brevkode,
            fields(loggingMeta),
        )
        true
    } else {
        log.info(
            "Sykmelding gjelder innenlands-sykmelding, brevkode: {}, {}",
            brevkode,
            fields(loggingMeta),
        )
        false
    }
}

data class FindJournalpostRequest(val query: String, val variables: Variables)

data class Variables(val journalpostId: String)

data class FindJournalpostResponse(
    val journalpost: Journalpost,
)

data class Journalpost(
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    val datoOpprettet: String?,
    val dokumenter: List<Dokument>,
    val journalposttype: String,
    val journalstatus: Journalstatus?,
    val kanal: String?,
    val kanalnavn: String?,
    val opprettetAvNavn: String?,
    val sak: Sak?,
    val skjerming: String?,
    val tema: String?,
    val temanavn: String?,
    val tittel: String?,
)

enum class Journalstatus {
    MOTTATT,
    JOURNALFOERT,
    FERDIGSTILT,
    EKSPEDERT,
    UNDER_ARBEID,
    FEILREGISTRERT,
    UTGAAR,
    AVBRUTT,
    UKJENT_BRUKER,
    RESERVERT,
    OPPLASTING_DOKUMENT,
    UKJENT,
}

data class Sak(
    val fagsakId: String?,
    val fagsaksystem: String?,
    val sakstype: String,
)

data class Dokument(
    val tittel: String?,
    val dokumentInfoId: String,
    val brevkode: String,
    val dokumentvarianter: List<Dokumentvarianter>,
)

data class Dokumentvarianter(
    val variantformat: Variantformat,
)

enum class Variantformat {
    ARKIV,
    FULLVERSJON,
    PRODUKSJON,
    PRODUKSJON_DLF,
    SLADDET,
    ORIGINAL
}

data class AvsenderMottaker(
    val id: String,
    val navn: String,
)

data class Bruker(
    val id: String?,
    val type: BrukerIdType?,
)

enum class BrukerIdType {
    AKTOERID,
    FNR,
    ORGNR,
}
