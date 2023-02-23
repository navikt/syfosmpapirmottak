package no.nav.syfo.domain

import no.nav.syfo.client.DokumentMedTittel
import java.time.LocalDateTime

data class JournalpostMetadata(
    val bruker: Bruker,
    @Deprecated("dokumenter skal ta over for denne")
    val dokumentInfoId: String?,
    val dokumenter: List<DokumentMedTittel>,
    val jpErIkkeJournalfort: Boolean,
    val gjelderUtland: Boolean,
    val datoOpprettet: LocalDateTime?,
    val dokumentInfoIdPdf: String,
)

data class Bruker(
    val id: String?,
    val type: String?,
)
