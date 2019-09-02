package no.nav.syfo.domain

data class JournalpostMetadata(
    val bruker: Bruker,
    val dokumenter: List<DokumentInfo>?
)

data class Bruker(
    val id: String?,
    val type: String?
)

data class DokumentInfo(
    val dokumentInfoId: String?,
    val dokumentvarianter: List<Dokumentvariant>?
)

data class Dokumentvariant(
    val variantformat: String?
)
