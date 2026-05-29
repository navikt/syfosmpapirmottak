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
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.xml.bind.JAXBException
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.Source
import javax.xml.transform.sax.SAXSource
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.azure.v2.AzureAdV2Token
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_HENTDOK_FEIL
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.skanningMetadataUnmarshaller
import org.xml.sax.InputSource

class SafDokumentClient(
    private val url: String,
    private val accessTokenClientV2: AzureAdV2Client,
    private val scope: String,
    private val httpClient: HttpClient,
) {

    private suspend fun getDokumentFraSaf(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        loggingMeta: LoggingMeta,
        dokumentVariant: DokumentVariant,
        contentType: ContentType,
    ): String {
        val accessToken = getAccessToken()

        val httpResponse: HttpResponse =
            httpClient.get(
                "$url/rest/hentdokument/$journalpostId/$dokumentInfoId/$dokumentVariant"
            ) {
                accept(contentType)
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

    private suspend fun getPdfDokumentFraSaf(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        loggingMeta: LoggingMeta,
        dokumentVariant: DokumentVariant,
    ): ByteArray {
        val accessToken = getAccessToken()

        val httpResponse: HttpResponse =
            httpClient.get(
                "$url/rest/hentdokument/$journalpostId/$dokumentInfoId/$dokumentVariant"
            ) {
                accept(ContentType.Application.Pdf)
                header("Authorization", "Bearer ${accessToken.accessToken}")
                header("Nav-Callid", msgId)
                header("Nav-Consumer-Id", "syfosmpapirmottak")
            }
        when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.error(
                    "Saf svarte med feilmelding ved henting av PDF-dokument for msgId {}, {}",
                    msgId,
                    fields(loggingMeta)
                )
                throw IOException(
                    "Saf svarte med feilmelding ved henting av PDF-dokument for msgId $msgId"
                )
            }
            NotFound -> {
                log.error("PDF-dokumentet finnes ikke for msgId {}, {}", msgId, fields(loggingMeta))
                throw SafNotFoundException("Fant ikke PDF-dokumentet for msgId $msgId i SAF")
            }
            else -> {
                log.info("Hentet PDF-dokument for msgId {}, {}", msgId, fields(loggingMeta))
                return httpResponse.body<ByteArray>()
            }
        }
    }



    suspend fun getXmlDokument(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        loggingMeta: LoggingMeta,
        dokumentVariant: DokumentVariant
    ): Skanningmetadata? {
        return try {
            val dokument =
                getDokumentFraSaf(
                    journalpostId,
                    dokumentInfoId,
                    msgId,
                    loggingMeta,
                    dokumentVariant,
                    ContentType.Application.Xml
                )
            log.info("Got document with id: $dokumentInfoId")
            safeUnmarshalSkanningmetadata(dokument.byteInputStream(Charsets.UTF_8))
        } catch (ex: JAXBException) {
            log.warn(
                "Klarte ikke å tolke OCR-dokument for dokument $dokumentInfoId, ${fields(loggingMeta)}",
                ex
            )
            PAPIRSM_HENTDOK_FEIL.inc()
            null
        }
    }

    suspend fun getPdfDokument(
        journalpostId: String,
        dokumentInfoId: String,
        msgId: String,
        loggingMeta: LoggingMeta,
    ): ByteArray {
        return try {
            getPdfDokumentFraSaf(
                journalpostId,
                dokumentInfoId,
                msgId,
                loggingMeta,
                DokumentVariant.ARKIV
            )
        } catch (ioex: IOException) {
            log.warn(
                "Klarte ikke å hente PDF-dokument for dokument $dokumentInfoId, ${fields(loggingMeta)}",
                ioex
            )
            throw ioex
        }

    }

    private suspend fun getAccessToken(): AzureAdV2Token {
        val accessToken = accessTokenClientV2.getAccessToken(scope)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for Saf")
        }
        return accessToken
    }

}

private fun safeUnmarshalSkanningmetadata(
    inputMessageText: ByteArrayInputStream
): Skanningmetadata {
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

enum class DokumentVariant {
    ORIGINAL,
    FULLVERSJON,
    ARKIV,
}

class SafNotFoundException(override val message: String) : Exception()
