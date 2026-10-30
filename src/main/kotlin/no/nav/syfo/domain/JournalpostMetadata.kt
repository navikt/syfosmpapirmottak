package no.nav.syfo.domain

import java.time.LocalDateTime
import no.nav.syfo.client.DokumentMedTittel
import no.nav.syfo.client.DokumentVariantFormat

data class JournalpostMetadata(
    val bruker: Bruker,
    @Deprecated("dokumenter skal ta over for denne") val dokumentInfoId: String?,
    val dokumenter: List<DokumentMedTittel>,
    val jpErIkkeJournalfort: Boolean,
    val gjelderUtland: Boolean,
    val datoOpprettet: LocalDateTime?,
    val dokumentInfoIdPdf: String,
    val alleDokumenter: List<DokumentInfo>,
)

data class Bruker(val id: String?, val type: String?)

data class DokumentInfo(
    val journalpostId: String,
    val dokumentInfoId: String,
    val filtype: String,
    val filUUID: String,
    val variantFormat: DokumentVariantFormat,
)
