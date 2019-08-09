package no.nav.syfo.model

import java.time.LocalDateTime
import no.nav.helse.sykSkanningMeta.AktivitetIkkeMuligType
import no.nav.helse.sykSkanningMeta.AktivitetType
import no.nav.helse.sykSkanningMeta.ArbeidsgiverType
import no.nav.helse.sykSkanningMeta.ArbeidsplassenType
import no.nav.helse.sykSkanningMeta.BehandlerType
import no.nav.helse.sykSkanningMeta.BidiagnoseType
import no.nav.helse.sykSkanningMeta.GradertSykmeldingType
import no.nav.helse.sykSkanningMeta.HovedDiagnoseType
import no.nav.helse.sykSkanningMeta.KontaktMedPasientType
import no.nav.helse.sykSkanningMeta.MedisinskVurderingType
import no.nav.helse.sykSkanningMeta.MedisinskeArsakerType
import no.nav.helse.sykSkanningMeta.MeldingTilNAVType
import no.nav.helse.sykSkanningMeta.PrognoseType
import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.helse.sykSkanningMeta.TilbakedateringType
import no.nav.helse.sykSkanningMeta.UtdypendeOpplysningerType
import no.nav.syfo.QuestionGroup
import no.nav.syfo.QuestionId

fun SykemeldingerType.toSykmelding(
    sykmeldingId: String,
    pasientAktoerId: String,
    legeAktoerId: String,
    msgId: String
) = Sykmelding(
        id = sykmeldingId,
        msgId = msgId,
        pasientAktoerId = pasientAktoerId,
        medisinskVurdering = medisinskVurdering.toMedisinskVurdering(),
        skjermesForPasient = medisinskVurdering.isSkjermesforpasient(),
        arbeidsgiver = arbeidsgiver.toArbeidsgiver(),
        perioder = listOf(mapPeriode(aktivitet)),
        prognose = prognose?.toPrognose(),
        utdypendeOpplysninger = utdypendeOpplysninger?.toMap() ?: mapOf(),
        tiltakArbeidsplassen = tiltak?.tiltakArbeidsplassen,
        tiltakNAV = tiltak?.tiltakNAV,
        andreTiltak = tiltak?.andreTiltak,
        meldingTilNAV = meldingTilNAV?.toMeldingTilNAV(),
        meldingTilArbeidsgiver = meldingTilArbeidsgiver,
        kontaktMedPasient = toKontaktMedPasient(kontaktMedPasient, tilbakedatering),
        behandletTidspunkt = kontaktMedPasient.behandletDato.atStartOfDay(),
        behandler = behandler.toBehandler(legeAktoerId),
        avsenderSystem = AvsenderSystem(
                navn = "papir",
                versjon = "1"
        ),
        syketilfelleStartDato = syketilfelleStartDato,
        signaturDato = LocalDateTime.now(),
        navnFastlege = "Ukjent"
)

fun BehandlerType.toBehandler(aktoerId: String) = Behandler(
        fornavn = "Ukjent",
        mellomnavn = "Ukjent",
        etternavn = "Ukjent",
        aktoerId = aktoerId,
        fnr = "",
        hpr = hpr.toString(),
        her = "",
        adresse = Adresse(
                gate = adresse,
                postnummer = 0,
                kommune = "",
                postboks = "",
                land = ""
        ),
        tlf = ""
)

fun toKontaktMedPasient(kontaktMedPasient: KontaktMedPasientType, tilbakedatering: TilbakedateringType) = KontaktMedPasient(
        kontaktDato = kontaktMedPasient.behandletDato,
        begrunnelseIkkeKontakt = tilbakedatering.tilbakebegrunnelse
)

fun MeldingTilNAVType.toMeldingTilNAV() = MeldingTilNAV(
        bistandUmiddelbart = bistandNAVUmiddelbart == "1".toBigInteger(),
        beskrivBistand = beskrivBistandNAV
)

fun UtdypendeOpplysningerType.toMap() = mapOf(
        QuestionGroup.GROUP_6_2.spmGruppeId to mapOf(
                QuestionId.ID_6_2_1.spmId to SporsmalSvar(QuestionId.ID_6_2_1.spmTekst, sykehistorie, listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)),
                QuestionId.ID_6_2_2.spmId to SporsmalSvar(QuestionId.ID_6_2_2.spmTekst, arbeidsevne, listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)),
                QuestionId.ID_6_2_3.spmId to SporsmalSvar(QuestionId.ID_6_2_3.spmTekst, behandlingsresultat, listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)),
                QuestionId.ID_6_2_4.spmId to SporsmalSvar(QuestionId.ID_6_2_4.spmTekst, planlagtBehandling, listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER))
        )
)

fun PrognoseType.toPrognose() = Prognose(
        arbeidsforEtterPeriode = friskmelding.arbeidsforEtterEndtPeriode.equals(1.toBigInteger()),
        hensynArbeidsplassen = friskmelding.beskrivHensynArbeidsplassen,
        erIArbeid = medArbeidsgiver?.let {
            ErIArbeid(
                    egetArbeidPaSikt = it.tilbakeSammeArbeidsgiver.equals(1.toBigInteger()),
                    annetArbeidPaSikt = it.tilbakeAnnenArbeidsgiver.equals(1.toBigInteger()),
                    arbeidFOM = it.tilbakeDato,
                    vurderingsdato = it.datoNyTilbakemelding
            )
        },
        erIkkeIArbeid = utenArbeidsgiver?.let {
            ErIkkeIArbeid(
                    arbeidsforPaSikt = it.tilbakeArbeid.equals(1.toBigInteger()),
                    arbeidsforFOM = it.tilbakeDato,
                    vurderingsdato = it.datoNyTilbakemelding
            )
        }
)

fun mapPeriode(aktivitet: AktivitetType) = Periode(
        fom = aktivitet.aktivitetIkkeMulig.periodeFOMDato,
        tom = aktivitet.aktivitetIkkeMulig.periodeTOMDato,
        aktivitetIkkeMulig = aktivitet.aktivitetIkkeMulig.toAktivitetIkkeMulig(),
        avventendeInnspillTilArbeidsgiver = aktivitet.innspillTilArbeidsgiver,
        behandlingsdager = aktivitet.behandlingsdager.antallBehandlingsdager.toInt(),
        gradert = aktivitet.gradertSykmelding?.toGradert(),
        reisetilskudd = aktivitet.reisetilskudd != null
)

fun GradertSykmeldingType.toGradert() = Gradert(
        reisetilskudd = reisetilskudd.equals(1.toBigInteger()),
        grad = sykmeldingsgrad.toInt() // TODO can cause  NumberFormatException
)

fun AktivitetIkkeMuligType.toAktivitetIkkeMulig() = AktivitetIkkeMulig(
        medisinskArsak = medisinskeArsaker?.toMedisinskArsak(),
        arbeidsrelatertArsak = arbeidsplassen?.toArbeidsrelatertArsak()
)

fun toMedisinskArsakType(medArsakerHindrer: String) =
        MedisinskArsakType.values().first { it.codeValue == medArsakerHindrer }

fun toArbeidsrelatertArsak(arbeidsplassenHindrer: String) =
        ArbeidsrelatertArsakType.values().first { it.codeValue == arbeidsplassenHindrer }

fun MedisinskeArsakerType.toMedisinskArsak() = MedisinskArsak(
        beskrivelse = medArsakerBesk,
        arsak = listOf(toMedisinskArsakType(medArsakerHindrer.toString()))
)

fun ArbeidsplassenType.toArbeidsrelatertArsak() = ArbeidsrelatertArsak(
        beskrivelse = arbeidsplassenBesk,
        arsak = listOf(toArbeidsrelatertArsak(arbeidsplassenHindrer.toString()))
)

fun MedisinskVurderingType.toMedisinskVurdering() = MedisinskVurdering(
        hovedDiagnose = hovedDiagnose.first()?.toDiagnose(),
        biDiagnoser = bidiagnose?.map(BidiagnoseType::toDiagnose) ?: listOf(),
        svangerskap = svangerskap.equals(1.toBigInteger()),
        yrkesskade = yrkesskade.equals(1.toBigInteger()),
        yrkesskadeDato = yrkesskadedato,
        annenFraversArsak = AnnenFraversArsak(
                beskrivelse = annenFraversArsak,
                // TODO this is not good at all...
                grunn = listOf(AnnenFraverGrunn.GODKJENT_HELSEINSTITUSJON)
        )
)

fun HovedDiagnoseType.toDiagnose() = Diagnose(diagnosekodeSystem, diagnosekode)

fun BidiagnoseType.toDiagnose() = Diagnose(diagnosekodeSystem, diagnosekode)

fun MedisinskVurderingType.isSkjermesforpasient(): Boolean =
        skjermesForPasient.equals(1.toBigInteger())

fun ArbeidsgiverType.toArbeidsgiver() = Arbeidsgiver(
        harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
        navn = navnArbeidsgiver,
        yrkesbetegnelse = yrkesbetegnelse,
        stillingsprosent = stillingsprosent.toInt()
)
