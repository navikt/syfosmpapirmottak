package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.log
import java.io.IOException

class NorskHelsenettClient(
    private val endpointUrl: String,
    private val accessTokenClient: AzureAdV2Client,
    private val resourceId: String,
    private val httpClient: HttpClient,
) {

    suspend fun finnBehandler(hprNummer: String, sykmeldingId: String): Behandler? {
        log.info("Henter behandler fra syfohelsenettproxy for sykmeldingId {}", sykmeldingId)

        val accessToken = accessTokenClient.getAccessToken(resourceId)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for Saf")
        }

        val httpResponse: HttpResponse = httpClient.get("$endpointUrl/api/v2/behandlerMedHprNummer") {
            accept(ContentType.Application.Json)

            headers {
                append("Authorization", "Bearer ${accessToken.accessToken}")
                append("Nav-CallId", sykmeldingId)
                append("hprNummer", hprNummer)
            }
        }
        return when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.error("Syfohelsenettproxy svarte med feilmelding for sykmeldingId {}", sykmeldingId)
                throw IOException("Syfohelsenettproxy svarte med feilmelding for $sykmeldingId")
            }
            NotFound -> {
                log.warn("Fant ikke behandler for HprNummer $hprNummer for sykmeldingId $sykmeldingId")
                null
            }
            else -> {
                log.info("Hentet behandler for sykmeldingId {}", sykmeldingId)
                httpResponse.call.response.body<Behandler>()
            }
        }
    }
}

data class Behandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
)

data class Godkjenning(
    val helsepersonellkategori: Kode? = null,
    val autorisasjon: Kode? = null,
)

data class Kode(
    val aktiv: Boolean,
    val oid: Int,
    val verdi: String?,
)
