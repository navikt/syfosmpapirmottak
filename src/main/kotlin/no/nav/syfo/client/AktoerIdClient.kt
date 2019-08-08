package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import type.BrukerIdType

@KtorExperimentalAPI
class AktoerIdClient(
    private val endpointUrl: String,
    private val stsClient: StsOidcClient
) {
    private val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer() {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    private suspend fun hentIdent(sokeIdent: List<String>, callId: String, identGruppe: String): Map<String, Aktor> =
            retry("hent_identer") {
                client.get<Map<String, Aktor>>("$endpointUrl/identer") {
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
                ?: error("Spoerringen til AktoerId returnerte ingen $identGruppe")
    }

    suspend fun finnAktorid(
        journalpost: FindJournalpostQuery.Journalpost,
        sykmeldingId: String
    ): String {
        val bruker = journalpost.bruker() ?: error("Journalpost mangler en bruker")
        val brukerId = bruker.id() ?: error("Journalpost mangler brukerid")
        return if (bruker.type() == BrukerIdType.AKTOERID) {
            brukerId
        } else {
            hentIdent(brukerId, sykmeldingId, "AktoerId")
        }
    }

    suspend fun finnFnr(
        journalpost: FindJournalpostQuery.Journalpost,
        sykmeldingId: String
    ): String {
        val bruker = journalpost.bruker() ?: error("Journalpost mangler en bruker")
        val brukerId = bruker.id() ?: error("Journalpost mangler brukerid")
        return if (bruker.type() == BrukerIdType.FNR) {
            brukerId
        } else {
            hentIdent(brukerId, sykmeldingId, "NorskIdent")
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
