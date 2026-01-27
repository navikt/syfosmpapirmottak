package no.nav.syfo.client

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.get
import io.ktor.client.statement.*
import io.ktor.http.headers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import no.nav.syfo.log
import no.nav.syfo.objectMapper

private data class Diagnosekode(
    val Kode: String,
    val Tekst_uten_lengdebegrensning: String,
    val Tekst_med_maksimalt_60_tegn: String?,
    val Gyldig_fra: String?,
    val Gyldig_til: String?,
    val Kodeverk: String?,
    val Tilhørighet_i_ICPC_2B: String?,
    val Foreldrekode: String?,
    val Foreldrekodetekst: String?
)

data class Icpc2BDiagnoser(
    val tekst: String?,
    val langTekst: String?,
    val parentCode: String,
    val kode: String,
)

fun getIcpc2Bdiagnoser(
    scope: CoroutineScope,
    path: String = "https://fat.kote.helsedirektoratet.no/api/code-systems/ICPC2/download/JSON"
): Deferred<Map<String, List<Icpc2BDiagnoser>>> {
    return scope.async(Dispatchers.IO) {
        try {
            val httpClient =
                HttpClient() {
                    install(HttpTimeout) {
                        socketTimeoutMillis = 30_000
                        connectTimeoutMillis = 30_000
                        requestTimeoutMillis = 30_000
                    }
                }
            val response =
                httpClient.get(path) { headers { append("Accept", "application/octet-stream") } }

            val diagnosekoder = response.readRawBytes()
            val codes = objectMapper.readValue<List<Diagnosekode>>(diagnosekoder)
            codes
                .asSequence()
                .filter { it.Tilhørighet_i_ICPC_2B == "TERM" }
                .filter { it.Gyldig_til == null }
                .filter { it.Kodeverk == "ICPC-2B" }
                .filter { it.Foreldrekode != null }
                .groupBy(
                    keySelector = { it.Kode.split(".").first() },
                    valueTransform = {
                        Icpc2BDiagnoser(
                            tekst = it.Tekst_med_maksimalt_60_tegn,
                            langTekst = it.Tekst_uten_lengdebegrensning,
                            parentCode = it.Foreldrekode
                                    ?: throw RuntimeException("Foreldrekode is null"),
                            kode = it.Kode,
                        )
                    }
                )
        } catch (e: Exception) {
            log.error("Could not get diagnosekoder", e)
            emptyMap()
        }
    }
}
