package no.nav.syfo.model

// model: https://confluence.adeo.no/pages/viewpage.action?pageId=287444683

data class Journalpost(
        val journalTilstand: JournalTilstand,
        val avsender: Avsender,
        val brukerListe: List<Bruker>,
        val arkivSak: ArkivSak,
        val tema: String,
        val tittel: String,
        val kanalReferanseId: String?,
        val forsendelseMottatt: String,
        val mottaksKanal: String?,
        val journalfEnhet: String?,
        val dokumentListe: List<Dokument>
)

data class Dokument(
        val dokumentId: String,
        val dokumentTypeId: String,
        val navSkjemaId: String?,
        val tittel: String,
        val dokumentKategori: String,
        val variant: List<Variant>,
        val logiskVedleggListe: List<LogiskVedlegg>
)

data class LogiskVedlegg(
        val logiskVedleggId: String,
        val logiskVedleggTittel: String
)

data class Variant(
        val arkivFilType: String,
        val variantFormat: String
)

data class ArkivSak(
        val arkivSakSystem: String,
        val arkivSakId: String
)

data class Bruker(
        val brukerType: BrukerType,
        val identifikator: String?
)

enum class BrukerType {
    PERSON, ORGANISASJON
}

enum class JournalTilstand {
    ENDELIG, MIDLERTIDIG, UTGAAR
}

data class Avsender(
        val navn: String,
        val avsenderType: AvsenderType,
        val identifikator: String
)

enum class AvsenderType {
    PERSON, ORGANISASJON
}
