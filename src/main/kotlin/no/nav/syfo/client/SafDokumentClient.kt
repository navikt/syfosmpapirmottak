package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.NotFound
import java.io.IOException
import javax.xml.bind.JAXBException
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.Source
import javax.xml.transform.sax.SAXSource
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_HENTDOK_FEIL
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.skanningMetadataUnmarshaller
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream

class SafDokumentClient(
    private val url: String,
    private val accessTokenClientV2: AzureAdV2Client,
    private val scope: String,
    private val httpClient: HttpClient,
) {

    private suspend fun hentDokumentFraSaf(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        loggingMeta: LoggingMeta
    ): String {
        val accessToken = accessTokenClientV2.getAccessToken(scope)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for Saf")
        }

        val httpResponse: HttpResponse =
            httpClient.get("$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ORIGINAL") {
                accept(ContentType.Application.Xml)
                header("Authorization", "Bearer ${accessToken.accessToken}")
                header("Nav-Callid", msgId)
                header("Nav-Consumer-Id", "syfosmpapirmottak")
            }
        when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.error(
                    "Saf svarte med feilmelding ved henting av dokument for msgId {}, {}",
                    msgId,
                    fields(loggingMeta)
                )
                throw IOException(
                    "Saf svarte med feilmelding ved henting av dokument for msgId $msgId"
                )
            }
            NotFound -> {
                log.error("Dokumentet finnes ikke for msgId {}, {}", msgId, fields(loggingMeta))
                throw SafNotFoundException("Fant ikke dokumentet for msgId $msgId i SAF")
            }
            else -> {
                log.info("Hentet OCR-dokument for msgId {}, {}", msgId, fields(loggingMeta))
                return httpResponse.body<String>()
            }
        }
    }

    suspend fun hentDokument(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        loggingMeta: LoggingMeta
    ): Skanningmetadata? {
        return try {
            val dokument = hentDokumentFraSaf(journalpostId, dokumentInfoId, msgId, loggingMeta)
            safeUnmarshalSkanningmetadata(dokument.byteInputStream(Charsets.UTF_8))
        } catch (ex: JAXBException) {
            log.warn("Klarte ikke å tolke OCR-dokument, ${fields(loggingMeta)}", ex)
            PAPIRSM_HENTDOK_FEIL.inc()
            null
        }
    }
}

private fun safeUnmarshalSkanningmetadata(inputMessageText: ByteArrayInputStream): Skanningmetadata {
    // Disable XXE
    val spf: SAXParserFactory = SAXParserFactory.newInstance()
    spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    spf.isNamespaceAware = true

    val xmlSource: Source =
        SAXSource(
            spf.newSAXParser().xmlReader,
            InputSource(inputMessageText),
        )

    return skanningMetadataUnmarshaller.unmarshal(xmlSource) as Skanningmetadata
}

class SafNotFoundException(override val message: String) : Exception()
