package no.nav.syfo.utland

data class DigitaliseringsoppgaveKafka(
    val oppgaveId: String,
    val fnr: String,
    val journalpostId: String,
    val dokumentInfoId: String?,
    val dokumenter: List<DokumentKafka>?,
    val type: String
)

data class DokumentKafka(
    val tittel: String,
    val dokumentInfoId: String
)
