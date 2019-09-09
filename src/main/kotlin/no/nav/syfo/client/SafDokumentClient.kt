package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.LoggingMeta
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_HENTDOK_FEIL
import no.nav.syfo.skanningMetadataUnmarshaller
import java.io.IOException
import java.io.StringReader
import javax.xml.bind.JAXBException

@KtorExperimentalAPI
class SafDokumentClient constructor(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {

    private suspend fun hentDokumentFraSaf(journalpostId: String, dokumentInfoId: String, msgId: String): String? = retry("hent_dokument") {
        val httpResponse = httpClient.get<HttpResponse>("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ORIGINAL") {
            accept(ContentType.Application.Xml)
            val oidcToken = oidcClient.oidcToken()
            this.header("Authorization", "Bearer ${oidcToken.access_token}")
            this.header("Nav-Callid", msgId)
            this.header("Nav-Consumer-Id", "syfosmpapirmottak")
        }
        if (httpResponse.status == InternalServerError) {
            log.error("Saf svarte med feilmelding ved henting av dokument for msgId {}", msgId)
            throw IOException("Saf svarte med feilmelding ved henting av dokument for msgId $msgId")
        }
        when (NotFound) {
            httpResponse.status -> {
                log.error("Dokumentet finnes ikke for msgId {}", msgId)
                null
            }
            else -> {
                log.info("Hentet OCR-dokument for msgId {}", msgId)
                httpResponse.call.response.receive<String>()
            }
        }
    }

    suspend fun hentDokument(journalpostId: String, dokumentInfoId: String, msgId: String, loggingMeta: LoggingMeta): Skanningmetadata? {
        return try {
            val dokument = hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId)
            dokument?.let {
                skanningMetadataUnmarshaller.unmarshal(StringReader(dokument)) as Skanningmetadata
            }
        } catch (ex: JAXBException) {
            log.warn("Klarte ikke å tolke OCR-dokument, {}: ${ex.message}", StructuredArguments.fields(loggingMeta))
            PAPIRSM_HENTDOK_FEIL.inc()
            null
        }
    }
}
