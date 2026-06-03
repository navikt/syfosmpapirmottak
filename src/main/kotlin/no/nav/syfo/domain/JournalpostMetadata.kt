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
    val alleDokumenter: Map<String, List<DokumentFilInfo>>?,
)

data class Bruker(
    val id: String?,
    val type: String?,
)

data class DokumentFilInfo(
    val filNamn: String,
    val filUUID: String,
    val filType: String,
    val variantFormat: DokumentVariantFormat,
)
