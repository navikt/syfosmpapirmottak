package no.nav.syfo.domain

data class JournalpostMetadata(
    val bruker: Bruker
)

data class Bruker(
    val id: String?,
    val type: String?
)
