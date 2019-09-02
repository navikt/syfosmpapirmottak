package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import java.time.LocalDate

@KtorExperimentalAPI
class SafDokumentClient constructor(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {

    private suspend fun hentDokumentFraSaf(journalpostId: String, dokumentInfoId: String, msgId: String): String = retry("hent_dokument") {
        httpClient.get<String>("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ORIGINAL") {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            this.header("Authorization", "Bearer ${oidcToken.access_token}")
            this.header("Nav-Callid", msgId)
            this.header("Nav-Consumer-Id", "syfosmpapirmottak")
        }
    }

    suspend fun hentDokument(journalpostId: String, dokumentInfoId: String, msgId: String): String {
        val xmlDokument = hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId)
        // map til object

        return hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId)
    }
}

data class Skanningmetadata(
    val sykemeldinger: Sykemeldinger
)

data class Sykemeldinger(
    val pasient: Pasient,
    val medisinskVurdering: MedisinskVurdering,
    val aktivitet: Aktivitet,
    val tilbakedatering: Tilbakedatering,
    val kontaktMedPasient: KontaktMedPasient,
    val behandler: Behandler
)

data class Pasient (val fnr: String)

data class MedisinskVurdering(val hovedDiagnose: HovedDiagnose)

data class HovedDiagnose(val diagnosekode: String, val diagnose: String)

data class Aktivitet(val aktivitetIkkeMulig: AktivitetIkkeMulig)

data class AktivitetIkkeMulig(
    val periodeFOMDato: LocalDate,
    val periodeTOMDato: LocalDate,
    val medisinskeArsaker: MedisinskeArsaker
)

data class MedisinskeArsaker(val medArsakerHindrer: String)

data class Tilbakedatering(val tilbakebegrunnelse: String)

data class KontaktMedPasient(val behandletDato: LocalDate)

data class Behandler(val hpr: String)
