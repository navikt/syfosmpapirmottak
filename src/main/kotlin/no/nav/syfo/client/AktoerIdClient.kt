package no.nav.syfo.client

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
import no.nav.syfo.model.IdentInfoResult
import type.BrukerIdType

@KtorExperimentalAPI
class AktoerIdClient(
    private val endpointUrl: String,
    private val stsClient: StsOidcClient
) {
    private val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    suspend fun fetchIdenter(personNumbers: List<String>, trackingId: String, identGruppe: String): Map<String, IdentInfoResult> =
            retry("get_aktoerids") {
                client.get<Map<String, IdentInfoResult>>("$endpointUrl/identer") {
                    accept(ContentType.Application.Json)
                    val oidcToken = stsClient.oidcToken()
                    headers {
                        append("Authorization", "Bearer ${oidcToken.access_token}")
                        append("Nav-Consumer-Id", "syfosmsak")
                        append("Nav-Call-Id", trackingId)
                        append("Nav-Personidenter", personNumbers.joinToString(","))
                    }
                    parameter("gjeldende", "true")
                    parameter("identgruppe", identGruppe)
                }
            }

    suspend fun findIdentOfType(
        brukerId: String,
        sykmeldingId: String,
        identGruppe: String
    ): String {
        log.info("Kaller AktoerId for aa hente en $identGruppe")
        val pasientAktoerId = fetchIdenter(listOf(brukerId), sykmeldingId, identGruppe)[brukerId]

        if (pasientAktoerId == null || pasientAktoerId.feilmelding != null) {
            throw RuntimeException("Pasient ikke funnet i $identGruppe, feilmelding: ${pasientAktoerId?.feilmelding}")
        }

        return pasientAktoerId.identer?.find { identInfo -> identInfo.gjeldende && identInfo.identgruppe == identGruppe }?.ident
                ?: error("Spoerringen til AktoerId returnerte ingen $identGruppe")
    }

    suspend fun findAktorid(
        journalpost: FindJournalpostQuery.Journalpost,
        sykmeldingId: String
    ): String {
        val bruker = journalpost.bruker() ?: error("Journalpost mangler en bruker")
        val brukerId = bruker.id() ?: error("Journalpost mangler brukerid")
        return if (bruker.type() == BrukerIdType.AKTOERID) {
            brukerId
        } else {
            findIdentOfType(brukerId, sykmeldingId, "AktoerId")
        }
    }

    suspend fun findFNR(
        journalpost: FindJournalpostQuery.Journalpost,
        sykmeldingId: String
    ): String {
        val bruker = journalpost.bruker() ?: error("Journalpost mangler en bruker")
        val brukerId = bruker.id() ?: error("Journalpost mangler brukerid")
        return if (bruker.type() == BrukerIdType.FNR) {
            brukerId
        } else {
            findIdentOfType(brukerId, sykmeldingId, "NorskIdent")
        }
    }
}