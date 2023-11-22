package no.nav.syfo.opprettsykmelding.model

data class Metadata(
    val type: String = "journalpost",
    val source: String = "syk-dig",
)

data class JournalpostMetadata(
    val journalpostId: String,
    val tema: String,
)

data class OpprettSykmeldingRecord(
    val metadata: Metadata,
    val data: JournalpostMetadata,
)
