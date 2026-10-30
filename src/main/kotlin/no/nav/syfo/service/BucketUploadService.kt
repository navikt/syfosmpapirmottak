package no.nav.syfo.service

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.domain.DokumentInfo
import no.nav.syfo.log

class BucketUploadService(
    private val safDokumentClient: SafDokumentClient,
    val storage: Storage,
    val bucketName: String,
) {
    suspend fun uploadDocuments(alleDokumenter: List<Pair<DokumentInfo, ByteArray>>) {
        coroutineScope {
            val jobs = mutableListOf<Deferred<Unit>>()

            alleDokumenter.forEach { dokument ->
                jobs +=
                    async(Dispatchers.IO) {
                        saveToBucket(
                            dokumentVariant = dokument.first,
                            hentetDokument = dokument.second,
                        )
                    }
            }

            jobs.awaitAll()
        }
    }

    private fun saveToBucket(dokumentVariant: DokumentInfo, hentetDokument: ByteArray) {
        val filType = dokumentVariant.filtype.lowercase()
        val blobName =
            "${dokumentVariant.journalpostId}_${dokumentVariant.dokumentInfoId}_${dokumentVariant.filUUID}.$filType"
        val existing = storage.get(bucketName, blobName)
        if (existing == null) {
            val blob =
                BlobInfo.newBuilder(BlobId.of(bucketName, blobName))
                    .setContentType(contentTypeForFilType(filType))
                    .build()
            storage.create(blob, hentetDokument)
            log.info(
                "Lagrer dokument i bucket for journalpostId: ${dokumentVariant.journalpostId}, dokumentInfoId: ${dokumentVariant.dokumentInfoId}, variant: ${dokumentVariant.variantFormat.name}. Namn på dokument er: $blobName"
            )
        } else {
            log.info(
                "Dokument finnes allerede i bucket for journalpostId: ${dokumentVariant.journalpostId}, dokumentInfoId: ${dokumentVariant.dokumentInfoId}, variant: ${dokumentVariant.variantFormat.name}"
            )
        }
    }

    private fun contentTypeForFilType(filType: String): String {
        return when (filType) {
            "pdf" -> "application/pdf"
            "xml" -> "application/xml"
            "json" -> "application/json"
            "txt" -> "text/plain"
            "jpg",
            "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
    }
}
