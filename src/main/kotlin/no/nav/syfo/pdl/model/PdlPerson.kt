package no.nav.syfo.pdl.model

data class PdlPerson(
    val navn: Navn,
    val fnr: String?,
    val aktorId: String?,
    val adressebeskyttelse: String?,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)
