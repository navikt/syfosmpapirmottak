package no.nav.syfo.domain

data class Sykmelder(
    val hprNummer: String,
    val fnr: String,
    val aktorId: String,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?
)
