package no.nav.syfo.service

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.syfo.client.DokumentVariant
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.util.LoggingMeta

class BucketUploadService(
    private val safDokumentClient: SafDokumentClient,
    val storage: Storage,
    val bucketName: String,
) {
    suspend fun uploadDocuments(
        journalpostId: String,
        dokumentInfoId: String?,
        dokumentInfoIdPdf: String,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ) {
        coroutineScope {
            val jobs = mutableListOf<Deferred<Unit>>()

            if (dokumentInfoId != null) {
                jobs +=
                    async(Dispatchers.IO) {
                        val ocrFilMedDokumentVariantOriginal =
                            safDokumentClient.getXmlDokumentAsXml(
                                journalpostId = journalpostId,
                                dokumentInfoId = dokumentInfoId,
                                msgId = sykmeldingId,
                                loggingMeta = loggingMeta,
                                dokumentVariant = DokumentVariant.ORIGINAL,
                            )

                        bucketUploadXml(ocrFilMedDokumentVariantOriginal, sykmeldingId)
                    }

                jobs +=
                    async(Dispatchers.IO) {
                        val ocrFilMedDokumentVariantFullversjon =
                            safDokumentClient.getXmlDokumentAsXml(
                                journalpostId = journalpostId,
                                dokumentInfoId = dokumentInfoId,
                                msgId = sykmeldingId,
                                loggingMeta = loggingMeta,
                                dokumentVariant = DokumentVariant.FULLVERSJON,
                            )
                        bucketUploadXml(ocrFilMedDokumentVariantFullversjon, sykmeldingId)
                    }
            }
            jobs +=
                async(Dispatchers.IO) {
                    // er rett bruk av dokumentinfoid? kan jo ligge ein pdf på den andre også ?
                    val pdf =
                        safDokumentClient.getPdfDokument(
                            journalpostId = journalpostId,
                            dokumentInfoId = dokumentInfoIdPdf,
                            msgId = sykmeldingId,
                            loggingMeta = loggingMeta,
                        )

                    bucketUploadPdf(pdf, sykmeldingId)
                }
            jobs.awaitAll()
        }
    }

    private fun bucketUploadPdf(pdf: ByteArray, sykmeldingId: String) {
        storage.get(bucketName, "$sykmeldingId/sykmelding.pdf")?.let {
            val blob =
                BlobInfo.newBuilder(BlobId.of(bucketName, "$sykmeldingId/sykmelding.pdf"))
                    .setContentType("application/pdf")
                    .build()
            storage.create(blob, pdf)
        }
    }

    private fun bucketUploadXml(ocrFilXml: String, sykmeldingId: String) {
        // nullcheck
        storage.get(bucketName, "$sykmeldingId/ocr.xml")?.let {
            val blob =
                BlobInfo.newBuilder(BlobId.of(bucketName, "$sykmeldingId/ocr.pdf"))
                    .setContentType("application/xml")
                    .build()

            storage.create(blob, ocrFilXml.toByteArray(Charsets.UTF_8))
        }
    }
}
