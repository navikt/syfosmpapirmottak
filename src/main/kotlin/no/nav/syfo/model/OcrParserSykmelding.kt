package no.nav.syfo.model

import kotlinx.serialization.Serializable

/**
 * The consumer-facing result of parsing a scanned sykmelding.
 *
 * This is the stable public model, intentionally decoupled from the internal parser model.
 * Debug/provenance fields (raw OCR fields, redaction markers) are deliberately omitted; parser
 * warnings are surfaced in [OcrParser OcrParserSykmeldingMeta].
 *
 * All dates are kept as raw `String`s exactly as extracted by OCR (they are frequently absent or
 * garbled, so no parsing/validation is imposed here).
 *
 * This is 1 to 1 compatible with [Sykmelding] in the `no.nav.sykmelding.api` package in
 * sykmelding-ocr-parser
 */
@Serializable
public data class OcrParserSykmelding(
    val formType: String,
    val formVersion: String?,
    val vurderingType: OcrParserVurderingType?,
    val legemeldtFravaerStart: String?,
    val pasient: OcrParserPasient,
    val arbeidsgiver: OcrParserArbeidsgiver,
    val diagnose: OcrParserDiagnose,
    val aktiviteter: List<OcrParserAktivitet>,
    val prognose: OcrParserPrognose,
    val tilleggsinformasjon: OcrParserTilleggsinformasjon,
    val tilbakedatering: OcrParserTilbakedatering,
    val sykmelder: OcrParserSykmelder,
    val meta: OcrParserSykmeldingMeta,
)

/**
 * Non-clinical metadata about how the document was parsed.
 *
 * [warnings] are parser-generated diagnostics and must remain free of patient data; they never
 * include raw OCR text or extracted field values.
 */
@Serializable
public data class OcrParserSykmeldingMeta(
    val formId: String?,
    val formVersion: String?,
    val parserId: String,
    val warnings: List<String> = emptyList(),
)

@Serializable
public enum class OcrParserVurderingType {
    FORSTE_VURDERING,
    PAFOLGENDE_VURDERING,
    PAFOLGENDE_ANNEN_SYKMELDER,
}

@Serializable
public enum class OcrParserAktivitetType {
    AKTIVITET_IKKE_MULIG,
    GRADERT,
    AVVENTENDE,
    BEHANDLINGSDAGER,
    REISETILSKUDD,
}

@Serializable
public data class OcrParserPasient(
    val navn: String? = null,
    val etternavn: String? = null,
    val fornavn: String? = null,
    val fnr: String? = null,
    val telefon: String? = null,
    val adresse: String? = null,
    val postnrSted: String? = null,
    val fastlege: String? = null,
    val navKontor: String? = null,
)

@Serializable
public data class OcrParserArbeidsgiver(
    val navn: String? = null,
    val yrkeStilling: String? = null,
    val stillingsprosent: Int? = null,
    val flereArbeidsgivere: Boolean = false,
)

@Serializable
public data class OcrParserDiagnose(
    val hoveddiagnoseKodesystem: String? = null,
    val hoveddiagnoseKode: String? = null,
    val hoveddiagnoseTekst: String? = null,
    val bidiagnoser: List<Bidiagnose> = emptyList(),
    val annenFravarsarsak: String? = null,
    val svangerskap: Boolean = false,
    val yrkesskade: Boolean = false,
    val skadedato: String? = null,
    val skjermingPaakrevet: Boolean = false,
)

@Serializable
public data class Bidiagnose(
    val kodesystem: String? = null,
    val kode: String? = null,
    val tekst: String? = null,
)

@Serializable
public sealed interface OcrParserAktivitet {
    public val fom: String?
    public val tom: String?
    public val type: OcrParserAktivitetType

    /** 4.3 100 % sykmelding — pasienten kan ikke være i arbeid. */
    @Serializable
    public data class IkkeMulig(
        override val fom: String? = null,
        override val tom: String? = null,
        val medisinskArsak: Boolean = false,
        val medisinskArsakType: String? = null,
        val medisinskArsakBeskrivelse: String? = null,
        val arbeidsrelatertArsak: Boolean = false,
    ) : OcrParserAktivitet {
        override val type: OcrParserAktivitetType
            get() = OcrParserAktivitetType.AKTIVITET_IKKE_MULIG
    }

    /** 4.2 Gradert sykmelding — pasienten kan være delvis i arbeid. */
    @Serializable
    public data class Gradert(
        override val fom: String? = null,
        override val tom: String? = null,
        val grad: Int? = null,
        val reisetilskudd: Boolean = false,
    ) : OcrParserAktivitet {
        override val type: OcrParserAktivitetType
            get() = OcrParserAktivitetType.GRADERT
    }

    /** 4.1 Avventende sykmelding. */
    @Serializable
    public data class Avventende(
        override val fom: String? = null,
        override val tom: String? = null,
        val innspillTilArbeidsgiver: String? = null,
    ) : OcrParserAktivitet {
        override val type: OcrParserAktivitetType
            get() = OcrParserAktivitetType.AVVENTENDE
    }

    /** 4.5 Behandlingsdager. */
    @Serializable
    public data class Behandlingsdager(
        override val fom: String? = null,
        override val tom: String? = null,
        val antallBehandlingsdager: Int? = null,
    ) : OcrParserAktivitet {
        override val type: OcrParserAktivitetType
            get() = OcrParserAktivitetType.BEHANDLINGSDAGER
    }

    /** Reisetilskudd som selvstendig aktivitet. */
    @Serializable
    public data class Reisetilskudd(
        override val fom: String? = null,
        override val tom: String? = null,
    ) : OcrParserAktivitet {
        override val type: OcrParserAktivitetType
            get() = OcrParserAktivitetType.REISETILSKUDD
    }
}

@Serializable public data class OcrParserPrognose(val arbeidsforEtterPeriode: Boolean = false)

@Serializable public data class OcrParserTilleggsinformasjon(val bistandNavOnskes: Boolean = false)

@Serializable
public data class OcrParserTilbakedatering(
    val kontaktDato: String? = null,
    val beskrivelse: String? = null,
)

@Serializable
public data class OcrParserSykmelder(
    val dato: String? = null,
    val navn: String? = null,
    val hprNummer: String? = null,
    val adresse: String? = null,
    val telefon: String? = null,
)
