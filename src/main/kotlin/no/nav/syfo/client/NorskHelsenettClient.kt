package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.util.KtorExperimentalAPI
import java.io.IOException
import no.nav.syfo.helpers.retry
import no.nav.syfo.log

class NorskHelsenettClient(
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClient,
    private val resourceId: String,
    private val httpClient: HttpClient
) {

    @KtorExperimentalAPI
    suspend fun finnBehandler(hprNummer: String, sykmeldingId: String): Behandler? = retry(
        callName = "finnbehandler",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L)) {
        log.info("Henter behandler fra syfohelsenettproxy for sykmeldingId {}", sykmeldingId)
        val httpResponse = httpClient.get<HttpStatement>("$endpointUrl/api/behandlerMedHprNummer") {
            accept(ContentType.Application.Json)
            val accessToken = accessTokenClient.hentAccessToken(resourceId)
            headers {
                append("Authorization", "Bearer $accessToken")
                append("Nav-CallId", sykmeldingId)
                append("hprNummer", hprNummer)
            }
        }.execute()
        if (httpResponse.status == InternalServerError) {
            log.error("Syfohelsenettproxy svarte med feilmelding for sykmeldingId {}", sykmeldingId)
            throw IOException("Syfohelsenettproxy svarte med feilmelding for $sykmeldingId")
        }
        when (NotFound) {
            httpResponse.status -> {
                log.warn("Fant ikke behandler for HprNummer $hprNummer for sykmeldingId $sykmeldingId")
                null
            }
            else -> {
                log.info("Hentet behandler for sykmeldingId {}", sykmeldingId)
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
