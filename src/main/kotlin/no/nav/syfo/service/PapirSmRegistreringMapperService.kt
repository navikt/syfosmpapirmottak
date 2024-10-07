package no.nav.syfo.service

import java.time.OffsetDateTime
import no.nav.helse.diagnosekoder.Diagnosekoder
import no.nav.helse.papirsykemelding.AktivitetType
import no.nav.helse.papirsykemelding.ArbeidsgiverType
import no.nav.helse.papirsykemelding.BehandlerType
import no.nav.helse.papirsykemelding.BidiagnoseType
import no.nav.helse.papirsykemelding.HovedDiagnoseType
import no.nav.helse.papirsykemelding.MedArbeidsgiverType
import no.nav.helse.papirsykemelding.MedisinskVurderingType
import no.nav.helse.papirsykemelding.PrognoseType
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.helse.papirsykemelding.UtdypendeOpplysningerType
import no.nav.helse.papirsykemelding.UtenArbeidsgiverType
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
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
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.SporsmalSvar
import no.nav.syfo.model.SvarRestriksjon

fun mapOcrFilTilPapirSmRegistrering(
    journalpostId: String,
    oppgaveId: String?,
    fnr: String?,
    aktorId: String?,
    dokumentInfoId: String?,
    datoOpprettet: OffsetDateTime?,
    sykmeldingId: String,
    sykmelder: Sykmelder?,
    ocrFil: Skanningmetadata?,
): PapirSmRegistering {
    val sykmelding = ocrFil?.sykemeldinger

    return PapirSmRegistering(
        journalpostId,
        oppgaveId,
        fnr,
        aktorId,
        dokumentInfoId,
        datoOpprettet,
        sykmeldingId,
        syketilfelleStartDato = sykmelding?.syketilfelleStartDato,
        arbeidsgiver = toArbeidsgiver(sykmelding?.arbeidsgiver),
        medisinskVurdering = toMedisinskVurdering(sykmelding?.medisinskVurdering),
        skjermesForPasient = sykmelding?.medisinskVurdering?.isSkjermesForPasient,
        perioder = toPerioder(aktivitetType = sykmelding?.aktivitet),
        prognose = toPrognose(sykmelding?.prognose),
        utdypendeOpplysninger = toUtdypendeOpplysninger(sykmelding?.utdypendeOpplysninger),
        tiltakArbeidsplassen = sykmelding?.tiltak?.tiltakArbeidsplassen,
        tiltakNAV = sykmelding?.tiltak?.tiltakNAV,
        andreTiltak = sykmelding?.tiltak?.andreTiltak,
        meldingTilNAV =
            sykmelding?.meldingTilNAV.let {
                MeldingTilNAV(
                    bistandUmiddelbart = it?.isBistandNAVUmiddelbart ?: false,
                    beskrivBistand = it?.beskrivBistandNAV,
                )
            },
        meldingTilArbeidsgiver = sykmelding?.meldingTilArbeidsgiver,
        kontaktMedPasient =
            sykmelding?.tilbakedatering?.let {
                KontaktMedPasient(
                    kontaktDato = it.tilbakeDato,
                    begrunnelseIkkeKontakt = it.tilbakebegrunnelse,
                )
            },
        behandletTidspunkt = sykmelding?.kontaktMedPasient?.behandletDato,
        behandler = toBehandler(sykmelder, sykmelding?.behandler),
    )
}

private fun toPerioder(aktivitetType: AktivitetType?): List<Periode> {
    val periodeListe = ArrayList<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode>()

    if (aktivitetType?.aktivitetIkkeMulig != null) {
        periodeListe.add(
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = aktivitetType.aktivitetIkkeMulig.periodeFOMDato
                periodeTOMDato = aktivitetType.aktivitetIkkeMulig.periodeTOMDato
                aktivitetIkkeMulig =
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                        medisinskeArsaker =
                            if (aktivitetType.aktivitetIkkeMulig.medisinskeArsaker != null) {
                                ArsakType().apply {
                                    beskriv =
                                        aktivitetType.aktivitetIkkeMulig.medisinskeArsaker
                                            .medArsakerBesk
                                    arsakskode.add(CS())
                                }
                            } else {
                                null
                            }
                        arbeidsplassen =
                            if (aktivitetType.aktivitetIkkeMulig.arbeidsplassen != null) {
                                ArsakType().apply {
                                    beskriv =
                                        aktivitetType.aktivitetIkkeMulig.arbeidsplassen
                                            .arbeidsplassenBesk
                                    arsakskode.add(CS())
                                }
                            } else {
                                null
                            }
                    }
                avventendeSykmelding = null
                gradertSykmelding = null
                behandlingsdager = null
                isReisetilskudd = false
            },
        )
    }

    if (aktivitetType?.gradertSykmelding != null) {
        periodeListe.add(
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = aktivitetType.gradertSykmelding.periodeFOMDato
                periodeTOMDato = aktivitetType.gradertSykmelding.periodeTOMDato
                aktivitetIkkeMulig = null
                avventendeSykmelding = null
                gradertSykmelding =
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                        isReisetilskudd = aktivitetType.gradertSykmelding.isReisetilskudd ?: false
                        sykmeldingsgrad =
                            try {
                                Integer.valueOf(aktivitetType.gradertSykmelding.sykmeldingsgrad)
                            } catch (e: NumberFormatException) {
                                0
                            }
                    }
                behandlingsdager = null
                isReisetilskudd = false
            },
        )
    }
    if (
        aktivitetType?.avventendeSykmelding != null &&
            !aktivitetType.innspillTilArbeidsgiver.isNullOrEmpty()
    ) {
        periodeListe.add(
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = aktivitetType.avventendeSykmelding.periodeFOMDato
                periodeTOMDato = aktivitetType.avventendeSykmelding.periodeTOMDato
                aktivitetIkkeMulig = null
                avventendeSykmelding =
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AvventendeSykmelding().apply {
                        innspillTilArbeidsgiver = aktivitetType.innspillTilArbeidsgiver
                    }
                gradertSykmelding = null
                behandlingsdager = null
                isReisetilskudd = false
            },
        )
    }
    if (aktivitetType?.behandlingsdager != null) {
        periodeListe.add(
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = aktivitetType.behandlingsdager.periodeFOMDato
                periodeTOMDato = aktivitetType.behandlingsdager.periodeTOMDato
                aktivitetIkkeMulig = null
                avventendeSykmelding = null
                gradertSykmelding = null
                behandlingsdager =
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
                        antallBehandlingsdagerUke =
                            aktivitetType.behandlingsdager?.antallBehandlingsdager?.toInt() ?: 1
                    }
                isReisetilskudd = false
            },
        )
    }
    if (aktivitetType?.reisetilskudd != null) {
        periodeListe.add(
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = aktivitetType.reisetilskudd.periodeFOMDato
                periodeTOMDato = aktivitetType.reisetilskudd.periodeTOMDato
                aktivitetIkkeMulig = null
                avventendeSykmelding = null
                gradertSykmelding = null
                behandlingsdager = null
                isReisetilskudd = true
            },
        )
    }
    if (periodeListe.isEmpty()) {
        return emptyList()
    }

    return periodeListe
        .filter { periode -> periode.periodeFOMDato != null && periode.periodeTOMDato != null }
        .map(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode::toPeriode)
}

private fun toBehandler(sykmelder: Sykmelder?, behandler: BehandlerType?): Behandler =
    Behandler(
        fornavn = sykmelder?.fornavn ?: "",
        mellomnavn = sykmelder?.mellomnavn,
        etternavn = sykmelder?.etternavn ?: "",
        aktoerId = sykmelder?.aktorId ?: "",
        fnr = sykmelder?.fnr ?: "",
        hpr = (sykmelder?.hprNummer ?: behandler?.hpr ?: "").toString(),
        her = "",
        adresse = Adresse("", 0, "", "", ""),
        tlf = (sykmelder?.telefonnummer ?: behandler?.telefon ?: "").toString(),
    )

private fun toUtdypendeOpplysninger(
    utdypendeOpplysninger: UtdypendeOpplysningerType?
): Map<String, Map<String, SporsmalSvar>> {
    val map = HashMap<String, SporsmalSvar>()

    if (utdypendeOpplysninger?.sykehistorie != null) {
        val id = "6.2.1"
        val sporsmalSvar =
            SporsmalSvar(
                sporsmal = "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon",
                svar = utdypendeOpplysninger.sykehistorie,
                restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER),
            )
        map[id] = sporsmalSvar
    }

    if (utdypendeOpplysninger?.arbeidsevne != null) {
        val id = "6.2.2"
        val sporsmalSvar =
            SporsmalSvar(
                sporsmal = "Hvordan påvirker sykdommen arbeidsevnen",
                svar = utdypendeOpplysninger.arbeidsevne,
                restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER),
            )
        map[id] = sporsmalSvar
    }

    if (utdypendeOpplysninger?.behandlingsresultat != null) {
        val id = "6.2.3"
        val sporsmalSvar =
            SporsmalSvar(
                sporsmal = "Har behandlingen frem til nå bedret arbeidsevnen",
                svar = utdypendeOpplysninger.behandlingsresultat,
                restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER),
            )
        map[id] = sporsmalSvar
    }

    if (utdypendeOpplysninger?.planlagtBehandling != null) {
        val id = "6.2.4"
        val sporsmalSvar =
            SporsmalSvar(
                sporsmal = "Beskriv pågående og planlagt henvisning,utredning og/eller behandling",
                svar = utdypendeOpplysninger.planlagtBehandling,
                restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER),
            )
        map[id] = sporsmalSvar
    }
    if (map.size > 0) {
        return mapOf<String, Map<String, SporsmalSvar>>(Pair("6.2", map))
    } else {
        return emptyMap()
    }
}

private fun toPrognose(prognose: PrognoseType?): Prognose? =
    Prognose(
        arbeidsforEtterPeriode = prognose?.friskmelding?.isArbeidsforEtterEndtPeriode ?: false,
        hensynArbeidsplassen = prognose?.friskmelding?.beskrivHensynArbeidsplassen,
        erIArbeid =
            if (prognose != null && prognose.medArbeidsgiver != null) {
                toErIArbeid(prognose.medArbeidsgiver)
            } else {
                null
            },
        erIkkeIArbeid =
            if (prognose != null && prognose.utenArbeidsgiver != null) {
                toErIkkeIArbeid(prognose.utenArbeidsgiver)
            } else {
                null
            },
    )

private fun toErIArbeid(medArbeidsgiver: MedArbeidsgiverType): ErIArbeid? {
    if (
        medArbeidsgiver.isTilbakeAnnenArbeidsgiver == null &&
            medArbeidsgiver.isTilbakeSammeArbeidsgiver == null &&
            medArbeidsgiver.tilbakeDato == null &&
            medArbeidsgiver.datoNyTilbakemelding == null
    ) {
        return null
    }
    return ErIArbeid(
        annetArbeidPaSikt = medArbeidsgiver.isTilbakeAnnenArbeidsgiver ?: false,
        egetArbeidPaSikt = medArbeidsgiver.isTilbakeSammeArbeidsgiver ?: false,
        arbeidFOM = medArbeidsgiver.tilbakeDato,
        vurderingsdato = medArbeidsgiver.datoNyTilbakemelding,
    )
}

private fun toErIkkeIArbeid(utenArbeidsgiver: UtenArbeidsgiverType): ErIkkeIArbeid? {
    if (
        utenArbeidsgiver.isTilbakeArbeid == null &&
            utenArbeidsgiver.tilbakeDato == null &&
            utenArbeidsgiver.datoNyTilbakemelding == null
    ) {
        return null
    }
    return ErIkkeIArbeid(
        arbeidsforPaSikt = utenArbeidsgiver.isTilbakeArbeid ?: false,
        arbeidsforFOM = utenArbeidsgiver.tilbakeDato,
        vurderingsdato = utenArbeidsgiver.datoNyTilbakemelding,
    )
}

private fun toArbeidsgiver(arbeidsgiver: ArbeidsgiverType?): Arbeidsgiver? =
    Arbeidsgiver(
        navn = arbeidsgiver?.navnArbeidsgiver,
        harArbeidsgiver =
            with(arbeidsgiver?.harArbeidsgiver?.lowercase()) {
                when {
                    this == null -> HarArbeidsgiver.INGEN_ARBEIDSGIVER
                    this.contains("ingen") -> HarArbeidsgiver.INGEN_ARBEIDSGIVER
                    this.contains("flere") -> HarArbeidsgiver.FLERE_ARBEIDSGIVERE
                    this.contains("en") -> HarArbeidsgiver.EN_ARBEIDSGIVER
                    this.isNotBlank() -> HarArbeidsgiver.EN_ARBEIDSGIVER
                    else -> HarArbeidsgiver.INGEN_ARBEIDSGIVER
                }
            },
        stillingsprosent = arbeidsgiver?.stillingsprosent?.toInt(),
        yrkesbetegnelse = arbeidsgiver?.yrkesbetegnelse,
    )

private fun toMedisinskVurdering(
    medisinskVurderingType: MedisinskVurderingType?
): MedisinskVurdering {
    return MedisinskVurdering(
        hovedDiagnose =
            medisinskVurderingType?.hovedDiagnose?.firstOrNull()?.let {
                toMedisinskVurderingDiagnose(it)
            },
        biDiagnoser = medisinskVurderingType?.bidiagnose?.map { toMedisinskVurderingDiagnose(it)!! }
                ?: ArrayList(),
        svangerskap = medisinskVurderingType?.isSvangerskap ?: false,
        yrkesskade = medisinskVurderingType?.isYrkesskade ?: false,
        yrkesskadeDato = medisinskVurderingType?.yrkesskadedato,
        annenFraversArsak = null, // Can't safely map this
    )
}

private fun toMedisinskVurderingDiagnose(hovedDiagnoseType: HovedDiagnoseType?): Diagnose? =
    toMedisinskVurderingDiagnose(
        diagnoseKode = hovedDiagnoseType?.diagnosekode,
        diagnoseKodeSystem = hovedDiagnoseType?.diagnosekodeSystem,
        diagnoseTekst = hovedDiagnoseType?.diagnose,
    )

private fun toMedisinskVurderingDiagnose(bidiagnoseType: BidiagnoseType?): Diagnose? =
    toMedisinskVurderingDiagnose(
        diagnoseKode = bidiagnoseType?.diagnosekode,
        diagnoseKodeSystem = bidiagnoseType?.diagnosekodeSystem,
        diagnoseTekst = bidiagnoseType?.diagnose,
    )

private fun toMedisinskVurderingDiagnose(
    diagnoseKodeSystem: String?,
    diagnoseKode: String?,
    diagnoseTekst: String?
): Diagnose {
    if (diagnoseKode != null) {
        val sanitisertDiagnoseKode = diagnoseKode.replace(".", "").replace(" ", "")

        val icd10Entry =
            Diagnosekoder.icd10.entries.firstOrNull {
                it.key.uppercase() == sanitisertDiagnoseKode.uppercase()
            }
        if (icd10Entry != null) {
            return Diagnose(
                kode = icd10Entry.value.code,
                system = Diagnosekoder.ICD10_CODE,
                tekst = icd10Entry.value.text,
            )
        }
        val icpc2Entry =
            Diagnosekoder.icpc2.entries.firstOrNull {
                it.key.uppercase() == sanitisertDiagnoseKode.uppercase()
            }
        if (icpc2Entry != null) {
            return Diagnose(
                kode = icpc2Entry.value.code,
                system = Diagnosekoder.ICPC2_CODE,
                tekst = icpc2Entry.value.text,
            )
        }
    }

    return Diagnose(
        kode = diagnoseKode ?: "",
        system = diagnoseKodeSystem ?: "",
        tekst = diagnoseTekst ?: ""
    )
}
