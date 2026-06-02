package no.nav.syfo.service

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.syfo.client.DokumentVariantFormat
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.util.LoggingMeta

class BucketUploadService(
    private val safDokumentClient: SafDokumentClient,
    val storage: Storage,
    val bucketName: String,
) {
    suspend fun uploadDocuments(
        journalpostId: String,
        alleDokumenter: Map<String, List<String>>,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ) {
        coroutineScope {
            val jobs = mutableListOf<Deferred<Unit>>()
            alleDokumenter.forEach { dokument ->
                dokument.value.forEach { dokumentVariant ->
                    if (dokumentVariant == DokumentVariantFormat.ORIGINAL.name) {
                        jobs +=
                            async(Dispatchers.IO) {
                                val ocrFilMedDokumentVariantOriginal =
                                    safDokumentClient.getXmlDokumentAsXml(
                                        journalpostId = journalpostId,
                                        dokumentInfoId = dokument.key,
                                        msgId = sykmeldingId,
                                        loggingMeta = loggingMeta,
                                        dokumentVariant = DokumentVariantFormat.ORIGINAL,
                                    )

                                bucketUploadXml(ocrFilMedDokumentVariantOriginal, sykmeldingId)
                            }
                    }
                    if (dokumentVariant == DokumentVariantFormat.FULLVERSJON.name) {
                        jobs +=
                            async(Dispatchers.IO) {
                                val ocrFilMedDokumentVariantFullversjon =
                                    safDokumentClient.getXmlDokumentAsXml(
                                        journalpostId = journalpostId,
                                        dokumentInfoId = dokument.key,
                                        msgId = sykmeldingId,
                                        loggingMeta = loggingMeta,
                                        dokumentVariant = DokumentVariantFormat.FULLVERSJON,
                                    )
                                bucketUploadXml(ocrFilMedDokumentVariantFullversjon, sykmeldingId)
                            }
                    }

                    if (dokumentVariant == DokumentVariantFormat.ARKIV.name) {
                        jobs +=
                            async(Dispatchers.IO) {
                                val pdf =
                                    safDokumentClient.getPdfDokument(
                                        journalpostId = journalpostId,
                                        dokumentInfoId = dokument.key,
                                        msgId = sykmeldingId,
                                        loggingMeta = loggingMeta,
                                    )

                                bucketUploadPdf(pdf, sykmeldingId)
                            }
                    }
                }
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
