package no.nav.syfo.domain

data class JournalpostMetadata(
    val bruker: Bruker,
    val dokumentInfoId: String?
)

data class Bruker(
    val id: String?,
    val type: String?
)
