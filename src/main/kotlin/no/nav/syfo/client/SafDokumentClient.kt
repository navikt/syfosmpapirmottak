package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.sykSkanningMeta.SkanningmetadataType
import no.nav.syfo.LoggingMeta
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_HENTDOK_FEIL
import no.nav.syfo.skanningMetadataUnmarshaller
import java.io.StringReader
import javax.xml.bind.JAXBException

@KtorExperimentalAPI
class SafDokumentClient constructor(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {

    private suspend fun hentDokumentFraSaf(journalpostId: String, dokumentInfoId: String, msgId: String): String = retry("hent_dokument") {
        httpClient.get<String>("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ORIGINAL") {
            accept(ContentType.Application.Xml)
            val oidcToken = oidcClient.oidcToken()
            this.header("Authorization", "Bearer ${oidcToken.access_token}")
            this.header("Nav-Callid", msgId)
            this.header("Nav-Consumer-Id", "syfosmpapirmottak")
        }
    }

    suspend fun hentDokument(journalpostId: String, dokumentInfoId: String, msgId: String, loggingMeta: LoggingMeta): SkanningmetadataType? {
        return try {
            skanningMetadataUnmarshaller.unmarshal(StringReader(hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId))) as SkanningmetadataType
        } catch (ex: JAXBException) {
            log.warn("Klarte ikke å tolke OCR-dokument: ${ex.message}, {}", loggingMeta)
            PAPIRSM_HENTDOK_FEIL.inc()
            null
        }
    }
}
