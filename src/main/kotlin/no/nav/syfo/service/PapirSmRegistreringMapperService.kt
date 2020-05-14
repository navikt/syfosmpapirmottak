package no.nav.syfo.service

import io.ktor.util.Hash
import no.nav.helse.sykSkanningMeta.ArbeidsgiverType
import no.nav.helse.sykSkanningMeta.BidiagnoseType
import no.nav.helse.sykSkanningMeta.HovedDiagnoseType
import no.nav.helse.sykSkanningMeta.MedisinskVurderingType
import no.nav.helse.sykSkanningMeta.PrognoseType
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.helse.sykSkanningMeta.UtdypendeOpplysningerType
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.ErIkkeIArbeid
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.MeldingTilNAV
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.SporsmalSvar
import no.nav.syfo.model.SvarRestriksjon
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.util.LoggingMeta
import java.time.LocalDateTime

fun mapOcrFilTilPapirSmRegistrering(
        journalpostId: String,
        fnr: String?,
        aktorId: String?,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        sykmeldingId: String,
        sykmelder: Sykmelder?,
        ocrFil: Skanningmetadata?
): PapirSmRegistering {

    val sykmelding = ocrFil?.sykemeldinger

    return PapirSmRegistering(journalpostId, fnr, aktorId, dokumentInfoId, datoOpprettet, sykmeldingId,
            syketilfelleStartDato = sykmelding?.syketilfelleStartDato,
            arbeidsgiver = toArbeidsgiver(sykmelding?.arbeidsgiver),
            medisinskVurdering = toMedisinskVurdering(sykmelding?.medisinskVurdering),
            aktivitet = Any(),
            prognose = toPrognose(sykmelding?.prognose),
            utdypendeOpplysninger = toUtdypendeOpplysninger(sykmelding?.utdypendeOpplysninger),
            tiltakArbeidsplassen = sykmelding?.tiltak?.tiltakArbeidsplassen,
            tiltakNAV = sykmelding?.tiltak?.tiltakNAV,
            andreTiltak = sykmelding?.tiltak?.andreTiltak,
            meldingTilNAV = sykmelding?.meldingTilNAV.let {
                MeldingTilNAV(bistandUmiddelbart = it?.isBistandNAVUmiddelbart ?: false,
                beskrivBistand = it?.beskrivBistandNAV)
            },
            meldingTilArbeidsgiver = sykmelding?.meldingTilArbeidsgiver,
            kontaktMedPasient = sykmelding?.kontaktMedPasient?.behandletDato.let {
                KontaktMedPasient(
                        kontaktDato = sykmelding?.kontaktMedPasient?.behandletDato,
                        begrunnelseIkkeKontakt = null
                )
            },
            behandletTidspunkt = sykmelding?.kontaktMedPasient?.behandletDato,
            behandler = toBehandler(sykmelder)
    )
}

fun toBehandler(sykmelder: Sykmelder?): Behandler = Behandler(
        fornavn = sykmelder?.fornavn?: "",
        mellomnavn = sykmelder?.mellomnavn,
        etternavn = sykmelder?.etternavn?: "",
        aktoerId = sykmelder?.aktorId?: "",
        fnr = sykmelder?.fnr?: "",
        hpr = sykmelder?.hprNummer,
        her = "",
        adresse = Adresse("", 0, "", "",""),
        tlf = sykmelder?.telefonnummer
)


fun toUtdypendeOpplysninger(utdypendeOpplysninger: UtdypendeOpplysningerType?): Map<String, Map<String, SporsmalSvar>> {

    val map = HashMap<String, SporsmalSvar>()

    if (utdypendeOpplysninger?.sykehistorie != null) {
        val id = "6.2.1"
        val sporsmalSvar = SporsmalSvar(
                sporsmal = "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon",
                svar = utdypendeOpplysninger?.sykehistorie,
                restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)
        )
        map[id] = sporsmalSvar
    }

    if (utdypendeOpplysninger?.arbeidsevne != null) {
        val id = "6.2.2"
        val sporsmalSvar = SporsmalSvar(
                sporsmal = "Hvordan påvirker sykdommen arbeidsevnen",
                svar = utdypendeOpplysninger?.arbeidsevne,
                restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)
        )
        map[id] = sporsmalSvar
    }

    if (utdypendeOpplysninger?.behandlingsresultat != null) {
        val id = "6.2.3"
        val sporsmalSvar = SporsmalSvar(
                sporsmal = "Har behandlingen frem til nå bedret arbeidsevnen",
                svar = utdypendeOpplysninger?.behandlingsresultat,
                restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)
        )
        map[id] = sporsmalSvar
    }

    if (utdypendeOpplysninger?.planlagtBehandling != null) {
        val id = "6.2.4"
        val sporsmalSvar = SporsmalSvar(
                sporsmal = "Beskriv pågående og planlagt henvisning,utredning og/eller behandling",
                svar = utdypendeOpplysninger?.planlagtBehandling,
                restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)
        )
        map[id] = sporsmalSvar
    }
    if (map.size > 0) {
        return mapOf<String, Map<String, SporsmalSvar>>(Pair("6.2", map))
    } else return emptyMap()
}

fun toPrognose(prognose: PrognoseType?): Prognose? = Prognose(
        arbeidsforEtterPeriode = prognose?.friskmelding?.isArbeidsforEtterEndtPeriode ?: true,
        hensynArbeidsplassen = prognose?.friskmelding?.beskrivHensynArbeidsplassen,
        erIArbeid = if (prognose != null && prognose.medArbeidsgiver != null) {
            ErIArbeid(
                    annetArbeidPaSikt = prognose.medArbeidsgiver.isTilbakeAnnenArbeidsgiver,
                    egetArbeidPaSikt = prognose.medArbeidsgiver.isTilbakeSammeArbeidsgiver,
                    arbeidFOM = prognose.medArbeidsgiver.tilbakeDato,
                    vurderingsdato = prognose.medArbeidsgiver.datoNyTilbakemelding
            )
        } else null,
        erIkkeIArbeid = if (prognose != null && prognose.utenArbeidsgiver != null) {
            ErIkkeIArbeid(
                    arbeidsforPaSikt = prognose.utenArbeidsgiver.isTilbakeArbeid,
                    arbeidsforFOM = prognose.utenArbeidsgiver.tilbakeDato,
                    vurderingsdato = prognose.utenArbeidsgiver.datoNyTilbakemelding
            )
        } else null
)

fun toArbeidsgiver(arbeidsgiver: ArbeidsgiverType?): Arbeidsgiver? = Arbeidsgiver(
        navn = arbeidsgiver?.navnArbeidsgiver,
        harArbeidsgiver = when (arbeidsgiver?.harArbeidsgiver) {
            "Flere arbeidsgivere" -> HarArbeidsgiver.FLERE_ARBEIDSGIVERE
            "Flerearbeidsgivere" -> HarArbeidsgiver.FLERE_ARBEIDSGIVERE
            "En arbeidsgiver" -> HarArbeidsgiver.EN_ARBEIDSGIVER
            "Ingen arbeidsgiver" -> HarArbeidsgiver.INGEN_ARBEIDSGIVER
            else -> HarArbeidsgiver.EN_ARBEIDSGIVER
        },
        stillingsprosent = arbeidsgiver?.stillingsprosent?.toInt(),
        yrkesbetegnelse = arbeidsgiver?.yrkesbetegnelse
)

fun toMedisinskVurdering(medisinskVurderingType: MedisinskVurderingType?): MedisinskVurdering {

    return MedisinskVurdering(
            hovedDiagnose = toMedisinskVurderingDiagnose(medisinskVurderingType?.hovedDiagnose?.first()),
            biDiagnoser = medisinskVurderingType?.bidiagnose?.map {
                toMedisinskVurderingDiagnose(it)!!
            }?: ArrayList(),
            svangerskap = medisinskVurderingType?.isSvangerskap?: false,
            yrkesskade = medisinskVurderingType?.isYrkesskade?: false,
            yrkesskadeDato = medisinskVurderingType?.yrkesskadedato,
            annenFraversArsak = null // Can't map this,
    )
}

fun toMedisinskVurderingDiagnose(hovedDiagnoseType: HovedDiagnoseType?): Diagnose? = toMedisinskVurderingDiagnose(
        diagnoseKode = hovedDiagnoseType?.diagnosekode,
        diagnoseKodeSystem = hovedDiagnoseType?.diagnosekodeSystem,
        diagnoseTekst = hovedDiagnoseType?.diagnose)

fun toMedisinskVurderingDiagnose(bidiagnoseType: BidiagnoseType?): Diagnose? = toMedisinskVurderingDiagnose(
        diagnoseKode = bidiagnoseType?.diagnosekode,
        diagnoseKodeSystem = bidiagnoseType?.diagnosekodeSystem,
        diagnoseTekst = bidiagnoseType?.diagnose)

fun toMedisinskVurderingDiagnose(diagnoseKodeSystem: String?, diagnoseKode: String?, diagnoseTekst: String?): Diagnose {
    if (diagnoseKode != null) {
        val sanitisertDiagnoseKode = when {
            diagnoseKode?.contains(".") -> {
                diagnoseKode?.replace(".", "").toUpperCase().replace(" ", "")
            } else -> diagnoseKode?.toUpperCase().replace(" ", "")
        }

        if (Diagnosekoder.icd10.containsKey(sanitisertDiagnoseKode)) {
            return Diagnose(kode = sanitisertDiagnoseKode,
                    system = Diagnosekoder.ICD10_CODE,
                    tekst = Diagnosekoder.icd10[sanitisertDiagnoseKode]?.text ?: ""
            )
        } else if (Diagnosekoder.icpc2.containsKey(sanitisertDiagnoseKode)) {
            return Diagnose(kode = sanitisertDiagnoseKode,
                    system = Diagnosekoder.ICPC2_CODE,
                    tekst = Diagnosekoder.icpc2[sanitisertDiagnoseKode]?.text ?: ""
            )
        }
    }

    return Diagnose(kode = diagnoseKode ?: "", system = diagnoseKodeSystem ?: "", tekst = diagnoseTekst ?: "")
}
