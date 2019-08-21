package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.log

@KtorExperimentalAPI
class AktoerIdClient(
    private val endpointUrl: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    private suspend fun hentIdent(sokeIdent: List<String>, callId: String, identGruppe: String): Map<String, Aktor> =
            retry("hent_identer") {
                httpClient.get<Map<String, Aktor>>("$endpointUrl/identer") {
                    accept(ContentType.Application.Json)
                    val oidcToken = stsClient.oidcToken()
                    headers {
                        append("Authorization", "Bearer ${oidcToken.access_token}")
                        append("Nav-Consumer-Id", "syfosmsak")
                        append("Nav-Call-Id", callId)
                        append("Nav-Personidenter", sokeIdent.joinToString(","))
                    }
                    parameter("gjeldende", "true")
                    parameter("identgruppe", identGruppe)
                }
            }

    private suspend fun hentIdent(
        brukerId: String,
        sykmeldingId: String,
        identGruppe: String
    ): String {
        log.info("Kaller AktoerId for aa hente en $identGruppe")
        val aktor = hentIdent(listOf(brukerId), sykmeldingId, identGruppe)[brukerId]

        if (aktor == null || aktor.feilmelding != null) {
            throw RuntimeException("Pasient ikke funnet i $identGruppe, feilmelding: ${aktor?.feilmelding}")
        }

        return aktor.identer?.find { ident -> ident.gjeldende && ident.identgruppe == identGruppe }?.ident
                ?: throw IllegalStateException("Spoerringen til AktoerId returnerte ingen $identGruppe")
    }

    suspend fun finnAktorid(
        fnr: String,
        sykmeldingId: String
    ): String? {
        return try {
            hentIdent(fnr, sykmeldingId, "AktoerId")
        } catch (e: Exception) {
            log.error("Kunne ikke hente aktørid for sykmeldingsid {}", sykmeldingId)
            null
        }
    }

    suspend fun finnFnr(
        aktorId: String,
        sykmeldingId: String
    ): String? {
        return try {
            hentIdent(aktorId, sykmeldingId, "NorskIdent")
        } catch (e: Exception) {
            log.error("Kunne ikke hente fnr for sykmeldingsid {}", sykmeldingId)
            null
        }
    }
}

data class Ident(
    val ident: String,
    val identgruppe: String,
    val gjeldende: Boolean
)

data class Aktor(
    val identer: List<Ident>? = null,
    val feilmelding: String? = null
)
