package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_HENTDOK_FEIL
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.skanningMetadataUnmarshaller
import java.io.IOException
import java.io.StringReader
import javax.xml.bind.JAXBException

@KtorExperimentalAPI
class SafDokumentClient constructor(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {

    private suspend fun hentDokumentFraSaf(journalpostId: String, dokumentInfoId: String, msgId: String, loggingMeta: LoggingMeta): String? = retry("hent_dokument") {
        val httpResponse = httpClient.get<HttpStatement>("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ORIGINAL") {
            accept(ContentType.Application.Xml)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("Nav-Callid", msgId)
            header("Nav-Consumer-Id", "syfosmpapirmottak")
        }.execute()

        when (httpResponse.status) {
            InternalServerError -> {
                log.error("Saf svarte med feilmelding ved henting av dokument for msgId {}, {}", msgId, fields(loggingMeta))
                throw IOException("Saf svarte med feilmelding ved henting av dokument for msgId $msgId")
            }
            NotFound -> {
                log.error("Dokumentet finnes ikke for msgId {}, {}", msgId, fields(loggingMeta))
                throw SafNotFoundException("Fant ikke dokumentet for msgId $msgId i SAF")
            }
            else -> {
                log.info("Hentet OCR-dokument for msgId {}, {}", msgId, fields(loggingMeta))
                httpResponse.call.response.receive<String>()
            }
        }
    }

    suspend fun hentDokument(journalpostId: String, dokumentInfoId: String, msgId: String, loggingMeta: LoggingMeta): Skanningmetadata? {
        return try {
            val dokument = hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId, loggingMeta)
            dokument?.let {
                skanningMetadataUnmarshaller.unmarshal(StringReader(dokument)) as Skanningmetadata
            }
        } catch (ex: JAXBException) {
            log.warn("Klarte ikke å tolke OCR-dokument, {}: ${ex.message}", fields(loggingMeta))
            PAPIRSM_HENTDOK_FEIL.inc()
            null
        }
    }
}

class SafNotFoundException(s: String) : Exception()
