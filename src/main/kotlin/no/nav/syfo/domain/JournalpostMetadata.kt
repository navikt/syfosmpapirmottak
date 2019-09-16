package no.nav.syfo.domain

data class JournalpostMetadata(
    val bruker: Bruker,
    val dokumentInfoId: String?,
    val jpErIkkeJournalfort: Boolean,
    val gjelderUtland: Boolean
)

data class Bruker(
    val id: String?,
    val type: String?
)
