package no.nav.syfo.service

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sykSkanningMeta.AktivitetType
import no.nav.helse.sykSkanningMeta.ArbeidsgiverType
import no.nav.helse.sykSkanningMeta.MedisinskVurderingType
import no.nav.helse.sykSkanningMeta.PrognoseType
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.helse.sykSkanningMeta.UtdypendeOpplysningerType
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.log
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.AnnenFraversArsak
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.ArbeidsrelatertArsak
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.ErIkkeIArbeid
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.MeldingTilNAV
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SporsmalSvar
import no.nav.syfo.model.SvarRestriksjon
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.util.LoggingMeta

class MappingService private constructor() {
    companion object {
        fun mapOcrFilTilReceivedSykmelding(
            skanningmetadata: Skanningmetadata,
            fnr: String,
            aktorId: String,
            datoOpprettet: LocalDateTime,
            sykmelder: Sykmelder,
            sykmeldingId: String,
            loggingMeta: LoggingMeta
        ): ReceivedSykmelding {

            if (skanningmetadata.sykemeldinger.pasient.fnr != fnr) {
                log.error("Fnr fra sykmelding matcher ikke fnr fra journalposthendelsen, avbryter.. {}", fields(loggingMeta))
                throw IllegalStateException("Fnr fra sykmelding matcher ikke fnr fra journalposthendelsen")
            }

            return ReceivedSykmelding(
                    sykmelding = tilSykmelding(sykemeldinger = skanningmetadata.sykemeldinger, sykmelder = sykmelder, aktorId = aktorId, sykmeldingId = sykmeldingId, loggingMeta = loggingMeta),
                    personNrPasient = fnr,
                    tlfPasient = null,
                    personNrLege = sykmelder.fnr,
                    navLogId = sykmeldingId,
                    msgId = sykmeldingId,
                    legekontorOrgNr = null,
                    legekontorHerId = null,
                    legekontorReshId = null,
                    legekontorOrgName = "",
                    mottattDato = datoOpprettet,
                    rulesetVersion = null,
                    fellesformat = "",
                    tssid = null)
        }

        fun tilSykmelding(sykemeldinger: SykemeldingerType, sykmelder: Sykmelder, aktorId: String, sykmeldingId: String, loggingMeta: LoggingMeta): Sykmelding {
            val periodeliste = tilPeriodeListe(sykemeldinger.aktivitet, loggingMeta)
            return Sykmelding(
                    id = sykmeldingId,
                    msgId = sykmeldingId,
                    pasientAktoerId = aktorId,
                    medisinskVurdering = tilMedisinskVurdering(sykemeldinger.medisinskVurdering, loggingMeta),
                    skjermesForPasient = sykemeldinger.medisinskVurdering?.isSkjermesForPasient ?: false,
                    arbeidsgiver = tilArbeidsgiver(sykemeldinger.arbeidsgiver, loggingMeta),
                    perioder = periodeliste,
                    prognose = sykemeldinger.prognose?.let { tilPrognose(sykemeldinger.prognose) },
                    utdypendeOpplysninger = tilUtdypendeOpplysninger(sykemeldinger.utdypendeOpplysninger),
                    tiltakArbeidsplassen = sykemeldinger.tiltak?.tiltakArbeidsplassen,
                    tiltakNAV = sykemeldinger.tiltak?.tiltakNAV,
                    andreTiltak = sykemeldinger.tiltak?.andreTiltak,
                    meldingTilNAV = sykemeldinger.meldingTilNAV?.let {
                        MeldingTilNAV(
                                bistandUmiddelbart = sykemeldinger.meldingTilNAV?.isBistandNAVUmiddelbart ?: false,
                                beskrivBistand = sykemeldinger.meldingTilNAV?.beskrivBistandNAV)
                    },
                    meldingTilArbeidsgiver = sykemeldinger.meldingTilArbeidsgiver,
                    kontaktMedPasient = KontaktMedPasient(
                            kontaktDato = sykemeldinger.kontaktMedPasient?.behandletDato,
                            begrunnelseIkkeKontakt = null),
                    behandletTidspunkt = velgRiktigKontaktOgSignaturDato(behandletDato = sykemeldinger.kontaktMedPasient?.behandletDato, periodeliste = periodeliste, loggingMeta = loggingMeta),
                    behandler = tilBehandler(sykmelder),
                    avsenderSystem = AvsenderSystem("Papirsykmelding", "1"),
                    syketilfelleStartDato = sykemeldinger.syketilfelleStartDato,
                    signaturDato = velgRiktigKontaktOgSignaturDato(behandletDato = sykemeldinger.kontaktMedPasient?.behandletDato, periodeliste = periodeliste, loggingMeta = loggingMeta),
                    navnFastlege = null
            )
        }

        fun tilMedisinskVurdering(medisinskVurderingType: MedisinskVurderingType, loggingMeta: LoggingMeta): MedisinskVurdering {
            if (medisinskVurderingType.hovedDiagnose.isNullOrEmpty()) {
                log.warn("Sykmelding mangler hoveddiagnose, avbryter.. {}", fields(loggingMeta))
                throw IllegalStateException("Sykmelding mangler hoveddiagnose")
            }

            val biDiagnoseListe: List<Diagnose>? = medisinskVurderingType.bidiagnose?.map {
                diagnoseFraDiagnosekode(it.diagnosekode, loggingMeta)
            }

            return MedisinskVurdering(
                    hovedDiagnose = diagnoseFraDiagnosekode(medisinskVurderingType.hovedDiagnose[0].diagnosekode, loggingMeta),
                    biDiagnoser = biDiagnoseListe ?: emptyList(),
                    svangerskap = medisinskVurderingType.isSvangerskap ?: false,
                    yrkesskade = medisinskVurderingType.isYrkesskade ?: false,
                    yrkesskadeDato = medisinskVurderingType.yrkesskadedato,
                    annenFraversArsak = medisinskVurderingType.annenFraversArsak?.let {
                        AnnenFraversArsak(
                                medisinskVurderingType.annenFraversArsak,
                                emptyList())
                    }
            )
        }

        fun diagnoseFraDiagnosekode(originalDiagnosekode: String, loggingMeta: LoggingMeta): Diagnose {
            val diagnosekode = if (originalDiagnosekode.contains(".")) {
                originalDiagnosekode.replace(".", "")
            } else {
                originalDiagnosekode
            }
            if (Diagnosekoder.icd10.containsKey(diagnosekode)) {
                log.info("Mappet $originalDiagnosekode til $diagnosekode for ICD10, {}", fields(loggingMeta))
                return Diagnose(
                        system = Diagnosekoder.ICD10_CODE,
                        kode = diagnosekode,
                        tekst = Diagnosekoder.icd10[diagnosekode]?.text ?: "")
            } else if (Diagnosekoder.icpc2.containsKey(diagnosekode)) {
                log.info("Mappet $originalDiagnosekode til $diagnosekode for ICPC2, {}", fields(loggingMeta))
                return Diagnose(
                        system = Diagnosekoder.ICPC2_CODE,
                        kode = diagnosekode,
                        tekst = Diagnosekoder.icpc2[diagnosekode]?.text ?: "")
            }
            log.warn("Diagnosekode $originalDiagnosekode tilhører ingen kjente kodeverk, {}", fields(loggingMeta))
            throw IllegalStateException("Diagnosekode $originalDiagnosekode tilhører ingen kjente kodeverk")
        }

        fun tilArbeidsgiver(arbeidsgiverType: ArbeidsgiverType?, loggingMeta: LoggingMeta): Arbeidsgiver {
            val harArbeidsgiver = when {
                arbeidsgiverType?.harArbeidsgiver == "Flere arbeidsgivere" -> HarArbeidsgiver.FLERE_ARBEIDSGIVERE
                arbeidsgiverType?.harArbeidsgiver == "Flerearbeidsgivere" -> HarArbeidsgiver.FLERE_ARBEIDSGIVERE
                arbeidsgiverType?.harArbeidsgiver == "En arbeidsgiver" -> HarArbeidsgiver.EN_ARBEIDSGIVER
                arbeidsgiverType?.harArbeidsgiver == "Ingen arbeidsgiver" -> HarArbeidsgiver.INGEN_ARBEIDSGIVER
                else -> {
                    log.warn("Klarte ikke å mappe {} til riktig harArbeidsgiver-verdi, bruker en arbeidsgiver som standard, {}", arbeidsgiverType?.harArbeidsgiver, fields(loggingMeta))
                    HarArbeidsgiver.EN_ARBEIDSGIVER
                }
            }

            return Arbeidsgiver(
                    harArbeidsgiver = harArbeidsgiver,
                    navn = arbeidsgiverType?.navnArbeidsgiver,
                    yrkesbetegnelse = arbeidsgiverType?.yrkesbetegnelse,
                    stillingsprosent = arbeidsgiverType?.stillingsprosent?.toInt()
            )
        }

        fun tilPeriodeListe(aktivitetType: AktivitetType, loggingMeta: LoggingMeta): List<Periode> {
            val periodeListe = ArrayList<Periode>()

            if (aktivitetType.aktivitetIkkeMulig != null) {
                periodeListe.add(Periode(
                        fom = aktivitetType.aktivitetIkkeMulig.periodeFOMDato,
                        tom = aktivitetType.aktivitetIkkeMulig.periodeTOMDato,
                        aktivitetIkkeMulig = AktivitetIkkeMulig(
                                medisinskArsak = if (aktivitetType.aktivitetIkkeMulig.medisinskeArsaker != null) MedisinskArsak(
                                        beskrivelse = aktivitetType.aktivitetIkkeMulig.medisinskeArsaker.medArsakerBesk,
                                        arsak = emptyList()) else null,
                                arbeidsrelatertArsak = if (aktivitetType.aktivitetIkkeMulig.arbeidsplassen != null) ArbeidsrelatertArsak(
                                        beskrivelse = aktivitetType.aktivitetIkkeMulig.arbeidsplassen.arbeidsplassenBesk,
                                        arsak = emptyList()) else null),
                        avventendeInnspillTilArbeidsgiver = null,
                        behandlingsdager = null,
                        gradert = null,
                        reisetilskudd = false)
                )
            }
            if (aktivitetType.gradertSykmelding != null) {
                periodeListe.add(Periode(
                        fom = aktivitetType.gradertSykmelding.periodeFOMDato,
                        tom = aktivitetType.gradertSykmelding.periodeTOMDato,
                        aktivitetIkkeMulig = null,
                        avventendeInnspillTilArbeidsgiver = null,
                        behandlingsdager = null,
                        gradert = Gradert(reisetilskudd = aktivitetType.gradertSykmelding.isReisetilskudd
                                ?: false, grad = Integer.valueOf(aktivitetType.gradertSykmelding.sykmeldingsgrad)),
                        reisetilskudd = false)
                )
            }
            if (aktivitetType.avventendeSykmelding != null && !aktivitetType.innspillTilArbeidsgiver.isNullOrEmpty()) {
                periodeListe.add(Periode(
                        fom = aktivitetType.avventendeSykmelding.periodeFOMDato,
                        tom = aktivitetType.avventendeSykmelding.periodeTOMDato,
                        aktivitetIkkeMulig = null,
                        avventendeInnspillTilArbeidsgiver = aktivitetType.innspillTilArbeidsgiver,
                        behandlingsdager = null,
                        gradert = null,
                        reisetilskudd = false)
                )
            }
            if (aktivitetType.behandlingsdager != null) {
                periodeListe.add(Periode(
                        fom = aktivitetType.behandlingsdager.periodeFOMDato,
                        tom = aktivitetType.behandlingsdager.periodeTOMDato,
                        aktivitetIkkeMulig = null,
                        avventendeInnspillTilArbeidsgiver = null,
                        behandlingsdager = aktivitetType.behandlingsdager.antallBehandlingsdager.toInt(),
                        gradert = null,
                        reisetilskudd = false)
                )
            }
            if (aktivitetType.reisetilskudd != null) {
                periodeListe.add(Periode(
                        fom = aktivitetType.reisetilskudd.periodeFOMDato,
                        tom = aktivitetType.reisetilskudd.periodeTOMDato,
                        aktivitetIkkeMulig = null,
                        avventendeInnspillTilArbeidsgiver = null,
                        behandlingsdager = null,
                        gradert = null,
                        reisetilskudd = true)
                )
            }
            if (periodeListe.isEmpty()) {
                log.warn("Could not find aktivitetstype, {}", fields(loggingMeta))
                throw IllegalStateException("Cound not find aktivitetstype")
            }
            return periodeListe
        }

        fun tilPrognose(prognoseType: PrognoseType): Prognose =
                Prognose(
                        arbeidsforEtterPeriode = prognoseType.friskmelding?.isArbeidsforEtterEndtPeriode ?: true,
                        hensynArbeidsplassen = prognoseType.friskmelding?.beskrivHensynArbeidsplassen,
                        erIArbeid = prognoseType.medArbeidsgiver?.let {
                            ErIArbeid(
                                    egetArbeidPaSikt = it.isTilbakeSammeArbeidsgiver,
                                    annetArbeidPaSikt = it.isTilbakeAnnenArbeidsgiver,
                                    arbeidFOM = it.tilbakeDato,
                                    vurderingsdato = it.datoNyTilbakemelding)
                        },
                        erIkkeIArbeid = prognoseType.utenArbeidsgiver?.let {
                            ErIkkeIArbeid(
                                    arbeidsforPaSikt = it.isTilbakeArbeid,
                                    arbeidsforFOM = it.tilbakeDato,
                                    vurderingsdato = it.datoNyTilbakemelding)
                        }
                )

        fun tilUtdypendeOpplysninger(utdypendeOpplysningerType: UtdypendeOpplysningerType?): Map<String, Map<String, SporsmalSvar>> {
            val utdypendeOpplysninger = HashMap<String, Map<String, SporsmalSvar>>()
            val sporsmalOgSvarMap = HashMap<String, SporsmalSvar>()

            // Spørsmålene kommer herfra: https://stash.adeo.no/projects/EIA/repos/nav-eia-external/browse/SM2013/xml/SM2013DynaSpm_1_5.xml
            utdypendeOpplysningerType?.sykehistorie?.let {
                sporsmalOgSvarMap["6.2.1"] = SporsmalSvar("Beskriv kort sykehistorie, symptomer og funn i dagens situasjon.", utdypendeOpplysningerType.sykehistorie, listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER))
            }
            utdypendeOpplysningerType?.arbeidsevne?.let {
                sporsmalOgSvarMap["6.2.2"] = SporsmalSvar("Hvordan påvirker sykdommen arbeidsevnen?", utdypendeOpplysningerType.arbeidsevne, listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER))
            }
            utdypendeOpplysningerType?.behandlingsresultat?.let {
                sporsmalOgSvarMap["6.2.3"] = SporsmalSvar("Har behandlingen frem til nå bedret arbeidsevnen?", utdypendeOpplysningerType.behandlingsresultat, listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER))
            }
            utdypendeOpplysningerType?.planlagtBehandling?.let {
                sporsmalOgSvarMap["6.2.4"] = SporsmalSvar("Beskriv pågående og planlagt henvisning,utredning og/eller behandling.", utdypendeOpplysningerType.planlagtBehandling, listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER))
            }

            if (sporsmalOgSvarMap.isNotEmpty()) {
                utdypendeOpplysninger["6.2"] = sporsmalOgSvarMap
            }

            return utdypendeOpplysninger
        }

        fun tilBehandler(sykmelder: Sykmelder): Behandler {
            return Behandler(
                    fornavn = sykmelder.fornavn ?: "",
                    mellomnavn = sykmelder.mellomnavn,
                    etternavn = sykmelder.etternavn ?: "",
                    aktoerId = sykmelder.aktorId,
                    fnr = sykmelder.fnr,
                    hpr = sykmelder.hprNummer,
                    her = null,
                    adresse = Adresse(gate = null, postnummer = null, kommune = null, postboks = null, land = null),
                    tlf = null
            )
        }

        fun velgRiktigKontaktOgSignaturDato(behandletDato: LocalDate?, periodeliste: List<Periode>, loggingMeta: LoggingMeta): LocalDateTime {
            behandletDato?.let {
                return LocalDateTime.of(it, LocalTime.NOON)
            }

            if (periodeliste.isEmpty()) {
                log.warn("Periodeliste er tom, kan ikke fortsette {}", fields(loggingMeta))
                throw IllegalStateException("Periodeliste er tom, kan ikke fortsette")
            }
            if (periodeliste.size > 1) {
                log.info("Periodeliste inneholder mer enn en periode {}", fields(loggingMeta))
            }

            periodeliste.forEach {
                if (it.aktivitetIkkeMulig != null) {
                    return LocalDateTime.of(it.fom, LocalTime.NOON)
                }
            }
            log.info("Periodeliste mangler aktivitetIkkeMulig, bruker FOM fra første periode {}", fields(loggingMeta))
            return LocalDateTime.of(periodeliste.first().fom, LocalTime.NOON)
        }
    }
}
