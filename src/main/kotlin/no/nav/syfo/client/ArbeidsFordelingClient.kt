package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry

@KtorExperimentalAPI
class ArbeidsFordelingClient(
    private val endpointUrl: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun finnBehandlendeEnhet(
        arbeidsfordelingRequest: ArbeidsfordelingRequest
    ): List<ArbeidsfordelingResponse>? =
            retry("hent_arbeidsfordeling") {
                httpClient.post<List<ArbeidsfordelingResponse>>("$endpointUrl/enheter/bestmatch") {
                    contentType(ContentType.Application.Json)
                    val oidcToken = stsClient.oidcToken()
                    header("Authorization", "Bearer ${oidcToken.access_token}")
                    body = arbeidsfordelingRequest
                }
            }
}

data class ArbeidsfordelingRequest(
    val tema: String,
    val geografiskOmraade: String?,
    val behandlingstema: String?,
    val behandlingstype: String?,
    val oppgavetype: String,
    val diskresjonskode: String?,
    val skjermet: Boolean
)

data class ArbeidsfordelingResponse(
    val enhetId: String,
    val navn: String,
    val enhetNr: String?,
    val antallRessurser: String?,
    val status: String?,
    val orgNivaa: String?,
    val type: String?,
    val organisasjonsnummer: String?,
    val underEtableringDato: String?,
    val aktiveringsdato: String?,
    val underAvviklingDato: String?,
    val nedleggelsesdato: String?,
    val oppgavebehandler: String?,
    val versjon: String?,
    val sosialeTjenester: String?,
    val kanalstrategi: String?,
    val orgNrTilKommunaltNavKontor: String?
)
