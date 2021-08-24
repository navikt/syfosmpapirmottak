package no.nav.syfo.domain

import java.time.LocalDateTime

data class JournalpostMetadata(
    val bruker: Bruker,
    val dokumentInfoId: String?,
    val jpErIkkeJournalfort: Boolean,
    val gjelderUtland: Boolean,
    val datoOpprettet: LocalDateTime?,
    val dokumentInfoIdPdf: String
)

data class Bruker(
    val id: String?,
    val type: String?
)
