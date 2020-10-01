package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.metrics.SAMHANDLERPRAKSIS_FOUND_COUNTER
import no.nav.syfo.metrics.SAMHANDLERPRAKSIS_NOT_FOUND_COUNTER
import java.util.Date

@KtorExperimentalAPI
class SarClient(
    private val endpointUrl: String,
    private val httpClient: HttpClient
) {
    suspend fun getSamhandler(ident: String): List<Samhandler> = retry("get_samhandler") {
        httpClient.get<List<Samhandler>>("$endpointUrl/rest/sar/samh") {
            accept(ContentType.Application.Json)
            parameter("ident", ident)
        }
    }
}


fun findBestSamhandlerPraksis(
        samhandlere: List<Samhandler>
): SamhandlerPraksis? {
    return getAktivOrInaktivSamhandlerPraksis(samhandlere).also {
        updateSamhandlerMetrics(it)
    }
}

private fun updateSamhandlerMetrics(it: SamhandlerPraksis?) {
    when (it) {
        null -> {
            SAMHANDLERPRAKSIS_NOT_FOUND_COUNTER.inc()
        }
        else -> {
            SAMHANDLERPRAKSIS_FOUND_COUNTER.labels(it.samh_praksis_type_kode).inc()
        }
    }
}

private fun getAktivOrInaktivSamhandlerPraksis(samhandlere: List<Samhandler>): SamhandlerPraksis? {
    val samhandlerPraksis = samhandlere.flatMap { it.samh_praksis }.groupBy { it.samh_praksis_status_kode }
    return samhandlerPraksis["aktiv"]?.firstOrNull() ?: samhandlerPraksis["inaktiv"]?.firstOrNull()
}

data class Samhandler(
        val samh_id: String,
        val navn: String,
        val samh_type_kode: String,
        val behandling_utfall_kode: String,
        val unntatt_veiledning: String,
        val godkjent_manuell_krav: String,
        val ikke_godkjent_for_refusjon: String,
        val godkjent_egenandel_refusjon: String,
        val godkjent_for_fil: String,
        val endringslogg_tidspunkt_siste: Date?,
        val samh_ident: List<Samhandlerident>,
        val samh_praksis: List<SamhandlerPraksis>
)

data class SamhandlerPraksis(
        val org_id: String?,
        val refusjon_type_kode: String?,
        val laerer: String?,
        val lege_i_spesialisering: String?,
        val tidspunkt_resync_periode: Date?,
        val tidspunkt_registrert: Date?,
        val samh_praksis_status_kode: String,
        val telefonnr: String?,
        val arbeids_kommune_nr: String?,
        val arbeids_postnr: String?,
        val arbeids_adresse_linje_1: String?,
        val arbeids_adresse_linje_2: String?,
        val arbeids_adresse_linje_3: String?,
        val arbeids_adresse_linje_4: String?,
        val arbeids_adresse_linje_5: String?,
        val her_id: String?,
        val post_adresse_linje_1: String?,
        val post_adresse_linje_2: String?,
        val post_adresse_linje_3: String?,
        val post_adresse_linje_4: String?,
        val post_adresse_linje_5: String?,
        val post_kommune_nr: String?,
        val post_postnr: String?,
        val tss_ident: String,
        val navn: String?,
        val ident: String?,
        val samh_praksis_type_kode: String?,
        val samh_id: String,
        val samh_praksis_id: String,
        val samh_praksis_periode: List<SamhandlerPeriode>
)

data class SamhandlerPeriode(
        val slettet: String,
        val gyldig_fra: Date,
        val gyldig_til: Date?,
        val samh_praksis_id: String,
        val samh_praksis_periode_id: String
)

data class Samhandlerident(
        val samh_id: String?,
        val samh_ident_id: String?,
        val ident: String?,
        val ident_type_kode: String?,
        val aktiv_ident: String?
)
