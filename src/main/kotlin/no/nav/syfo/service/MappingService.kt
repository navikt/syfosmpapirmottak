package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.sykSkanningMeta.AktivitetType
import no.nav.helse.sykSkanningMeta.ArbeidsgiverType
import no.nav.helse.sykSkanningMeta.MedisinskVurderingType
import no.nav.helse.sykSkanningMeta.PrognoseType
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.helse.sykSkanningMeta.UtdypendeOpplysningerType
import no.nav.syfo.LoggingMeta
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MappingService {

    fun mapOcrFilTilReceivedSykmelding(
        skanningmetadata: Skanningmetadata,
        fnr: String,
        aktorId: String,
        datoOpprettet: LocalDateTime,
        sykmelder: Sykmelder,
        sykmeldingId: String,
        loggingMeta: LoggingMeta): ReceivedSykmelding {
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
        return Sykmelding(
            id = sykmeldingId,
            msgId = sykmeldingId,
            pasientAktoerId = aktorId,
            medisinskVurdering = tilMedisinskVurdering(sykemeldinger.medisinskVurdering, loggingMeta),
            skjermesForPasient = sykemeldinger.medisinskVurdering?.isSkjermesForPasient ?: false,
            arbeidsgiver = tilArbeidsgiver(sykemeldinger.arbeidsgiver, loggingMeta),
            perioder = tilPeriodeListe(sykemeldinger.aktivitet),
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
            behandletTidspunkt = velgRiktigKontaktOgSignaturDato(behandletDato = sykemeldinger.kontaktMedPasient?.behandletDato, syketilfelleStartDato = sykemeldinger.syketilfelleStartDato, loggingMeta = loggingMeta),
            behandler = tilBehandler(sykmelder),
            avsenderSystem = AvsenderSystem("Papirsykmelding", "1"),
            syketilfelleStartDato = sykemeldinger.syketilfelleStartDato,
            signaturDato = velgRiktigKontaktOgSignaturDato(behandletDato = sykemeldinger.kontaktMedPasient?.behandletDato, syketilfelleStartDato = sykemeldinger.syketilfelleStartDato, loggingMeta = loggingMeta),
            navnFastlege = null
        )
    }

    fun tilMedisinskVurdering(medisinskVurderingType: MedisinskVurderingType, loggingMeta: LoggingMeta): MedisinskVurdering {
        if (medisinskVurderingType.hovedDiagnose.isNullOrEmpty()) {
            log.error("Sykmelding mangler hoveddiagnose, avbryter.. {}", fields(loggingMeta))
            throw IllegalStateException("Sykmelding mangler hoveddiagnose")
        }

        val biDiagnoseListe: List<Diagnose>? = medisinskVurderingType.bidiagnose?.map {
            Diagnose(
                system = diagnosekodeSystemFraDiagnosekode(it.diagnosekode, loggingMeta),
                kode = it.diagnosekode)
        }

        return MedisinskVurdering(
            hovedDiagnose = Diagnose(
                system = diagnosekodeSystemFraDiagnosekode(medisinskVurderingType.hovedDiagnose[0].diagnosekode, loggingMeta),
                kode = medisinskVurderingType.hovedDiagnose[0].diagnosekode),
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

    fun diagnosekodeSystemFraDiagnosekode(diagnosekode: String, loggingMeta: LoggingMeta): String {
        if (Diagnosekoder.icd10.containsKey(diagnosekode)) {
            return Diagnosekoder.ICD10_CODE
        } else if (Diagnosekoder.icpc2.containsKey(diagnosekode)) {
            return Diagnosekoder.ICPC2_CODE
        }
        log.error("Diagnosekode $diagnosekode tilhører ingen kjente kodeverk, {}", fields(loggingMeta))
        throw IllegalStateException("Diagnosekode $diagnosekode tilhører ingen kjente kodeverk")
    }

    fun tilArbeidsgiver(arbeidsgiverType: ArbeidsgiverType, loggingMeta: LoggingMeta): Arbeidsgiver {
        val harArbeidsgiver = when {
            arbeidsgiverType.harArbeidsgiver == "Flere arbeidsgivere" -> HarArbeidsgiver.FLERE_ARBEIDSGIVERE
            arbeidsgiverType.harArbeidsgiver == "En arbeidsgiver" -> HarArbeidsgiver.EN_ARBEIDSGIVER
            arbeidsgiverType.harArbeidsgiver == "Ingen arbeidsgiver" -> HarArbeidsgiver.INGEN_ARBEIDSGIVER
            else -> {
                log.error("Klarte ikke å mappe {} til riktig harArbeidsgiver-verdi, {}", arbeidsgiverType.harArbeidsgiver, fields(loggingMeta))
                throw IllegalStateException("Klarte ikke å mappe harArbeidsgiver")
            }
        }

        return Arbeidsgiver(
            harArbeidsgiver = harArbeidsgiver,
            navn = arbeidsgiverType.navnArbeidsgiver,
            yrkesbetegnelse = arbeidsgiverType.yrkesbetegnelse,
            stillingsprosent = arbeidsgiverType.stillingsprosent?.toInt()
        )
    }

    fun tilPeriodeListe(aktivitetType: AktivitetType): List<Periode> {
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
                gradert = Gradert(reisetilskudd = aktivitetType.gradertSykmelding.isReisetilskudd, grad = Integer.valueOf(aktivitetType.gradertSykmelding.sykmeldingsgrad)),
                reisetilskudd = false)
            )
        }
        if (aktivitetType.avventendeSykmelding != null) {
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

    fun velgRiktigKontaktOgSignaturDato(behandletDato: LocalDate?, syketilfelleStartDato: LocalDate?, loggingMeta: LoggingMeta): LocalDateTime {
        behandletDato?.let {
            return LocalDateTime.of(it, LocalTime.NOON)
        }
        syketilfelleStartDato?.let {
            return LocalDateTime.of(it, LocalTime.NOON)
        }
        log.error("Mangler både behandletDato og syketilfelleStartDato, kan ikke fortsette {}", fields(loggingMeta))
        throw IllegalStateException("Mangler både behandletDato og syketilfelleStartDato, kan ikke fortsette")
    }
}