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
import no.nav.syfo.domain.DokumentFilInfo
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

class BucketUploadService(
    private val safDokumentClient: SafDokumentClient,
    val storage: Storage,
    val bucketName: String,
) {
    suspend fun uploadDocuments(
        journalpostId: String,
        alleDokumenter: Map<String, List<DokumentFilInfo>>,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ) {
        coroutineScope {
            val jobs = mutableListOf<Deferred<Unit>>()
            alleDokumenter.forEach { dokument ->
                dokument.value.forEach { dokumentVariant ->
                    jobs +=
                        async(Dispatchers.IO) {
                            val hentetDokument =
                                safDokumentClient.getDocument(
                                    journalpostId = journalpostId,
                                    dokumentInfoId = dokument.key,
                                    dokumentVariant = dokumentVariant,
                                    loggingMeta = loggingMeta,
                                    msgId = sykmeldingId,
                                )
                            saveToBucket(
                                journalpostId = journalpostId,
                                dokumentInfoId = dokument.key,
                                dokumentVariant = dokumentVariant,
                                hentetDokument = hentetDokument,
                            )
                        }
                }
            }

            jobs.awaitAll()
        }
    }

    private fun saveToBucket(
        journalpostId: String,
        dokumentInfoId: String,
        dokumentVariant: DokumentFilInfo,
        hentetDokument: ByteArray
    ) {
        log.info("Preparing to save document in bucket, journalpostId: $journalpostId, dokumentinfoId: $dokumentInfoId , fil UUID: ${dokumentVariant.filUUID},  dokumentVariant: ${dokumentVariant.variantFormat.name}")
        val blobName =
            "${journalpostId}_${dokumentInfoId}_${dokumentVariant.filUUID}.${dokumentVariant.filType}"
        val existing = storage.get(bucketName, blobName)
        if(existing == null) {
            val blob =
                BlobInfo.newBuilder(BlobId.of(bucketName, blobName))
                    .setContentType("application/${dokumentVariant.filType}")
                    .build()
            storage.create(blob, hentetDokument)
            log.info("Lagrer dokument i bucket for journalpostId: $journalpostId, dokumentInfoId: $dokumentInfoId, variant: ${dokumentVariant.variantFormat.name}")
        }
        else {
            log.info("Dokument finnes allerede i bucket for journalpostId: $journalpostId, dokumentInfoId: $dokumentInfoId, variant: ${dokumentVariant.variantFormat.name}")
        }
    }
}
