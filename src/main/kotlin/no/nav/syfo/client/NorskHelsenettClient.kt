package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import java.io.IOException

class NorskHelsenettClient(
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClientV2,
    private val resourceId: String,
    private val httpClient: HttpClient
) {

    suspend fun finnBehandler(hprNummer: String, sykmeldingId: String): Behandler? = retry(
        callName = "finnbehandler",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L)
    ) {
        log.info("Henter behandler fra syfohelsenettproxy for sykmeldingId {}", sykmeldingId)
        try {
            return@retry httpClient.get<Behandler>("$endpointUrl/api/v2/behandlerMedHprNummer") {
                accept(ContentType.Application.Json)
                val accessToken = accessTokenClient.getAccessTokenV2(resourceId)
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("Nav-CallId", sykmeldingId)
                    append("hprNummer", hprNummer)
                }
            }.also {
                log.info("Hentet behandler for sykmeldingId {}", sykmeldingId)
            }
        } catch (e: Exception) {
            if (e is ClientRequestException && e.response.status == NotFound) {
                log.warn("Fant ikke behandler for HprNummer $hprNummer for sykmeldingId $sykmeldingId")
                null
            } else {
                log.error("Syfohelsenettproxy svarte med feilmelding for sykmeldingId {}", sykmeldingId)
                throw IOException("Syfohelsenettproxy svarte med feilmelding for $sykmeldingId")
            }
        }
    }
}

data class Behandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?
)

data class Godkjenning(
    val helsepersonellkategori: Kode? = null,
    val autorisasjon: Kode? = null
)

data class Kode(
    val aktiv: Boolean,
    val oid: Int,
    val verdi: String?
)
