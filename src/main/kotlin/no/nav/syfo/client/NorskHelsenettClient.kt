package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import java.io.IOException
import no.nav.syfo.helpers.retry
import no.nav.syfo.log

class NorskHelsenettClient(
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClient,
    private val resourceId: String,
    private val httpClient: HttpClient
) {

    suspend fun finnBehandler(hprNummer: String, msgId: String): Behandler? = retry(
        callName = "finnbehandler",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L)) {
        log.info("Henter behandler fra syfohelsenettproxy for msgId {}", msgId)
        val httpResponse = httpClient.get<HttpResponse>("$endpointUrl/api/behandlerMedHprNummer") {
            accept(ContentType.Application.Json)
            val accessToken = accessTokenClient.hentAccessToken(resourceId)
            headers {
                append("Authorization", "Bearer $accessToken")
                append("Nav-CallId", msgId)
                append("hprNummer", hprNummer)
            }
        }
        if (httpResponse.status == InternalServerError) {
            log.error("Syfohelsenettproxy svarte med feilmelding for msgId {}", msgId)
            throw IOException("Syfohelsenettproxy svarte med feilmelding for $msgId")
        }
        when (NotFound) {
            httpResponse.status -> {
                log.error("HprNummer mangler i request for msgId {}", msgId)
                null
            }
            else -> {
                log.info("Hentet behandler for msgId {}", msgId)
                httpResponse.call.response.receive<Behandler>()
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
