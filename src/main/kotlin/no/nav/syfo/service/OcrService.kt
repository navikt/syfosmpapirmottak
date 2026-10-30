package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.papirsykemelding.AktivitetIkkeMuligType
import no.nav.helse.papirsykemelding.AktivitetType
import no.nav.helse.papirsykemelding.ArbeidsgiverType
import no.nav.helse.papirsykemelding.ArbeidsplassenType
import no.nav.helse.papirsykemelding.AvventendeSykmeldingType
import no.nav.helse.papirsykemelding.BehandlerType
import no.nav.helse.papirsykemelding.BehandlingsdagerType
import no.nav.helse.papirsykemelding.BidiagnoseType
import no.nav.helse.papirsykemelding.FriskmeldingType
import no.nav.helse.papirsykemelding.GradertSykmeldingType
import no.nav.helse.papirsykemelding.HovedDiagnoseType
import no.nav.helse.papirsykemelding.KontaktMedPasientType
import no.nav.helse.papirsykemelding.MedisinskVurderingType
import no.nav.helse.papirsykemelding.MedisinskeArsakerType
import no.nav.helse.papirsykemelding.MeldingTilNAVType
import no.nav.helse.papirsykemelding.PasientType
import no.nav.helse.papirsykemelding.PrognoseType
import no.nav.helse.papirsykemelding.ReisetilskuddType
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.helse.papirsykemelding.SykemeldingerType
import no.nav.helse.papirsykemelding.TilbakedateringType
import no.nav.helse.papirsykemelding.TiltakType
import no.nav.helse.papirsykemelding.UtdypendeOpplysningerType
import no.nav.syfo.domain.DokumentInfo
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import no.nav.tsm.sykmelding.model.CheckboxState
import no.nav.tsm.sykmelding.model.HarArbeidsgiver
import no.nav.tsm.sykmelding.model.OcrCrossChecked
import no.nav.tsm.sykmelding.model.OcrParserAktivitet
import no.nav.tsm.sykmelding.model.OcrParserArbeidsgiver
import no.nav.tsm.sykmelding.model.OcrParserBehandler
import no.nav.tsm.sykmelding.model.OcrParserBistandNav
import no.nav.tsm.sykmelding.model.OcrParserDiagnose
import no.nav.tsm.sykmelding.model.OcrParserMedisinskVurdering
import no.nav.tsm.sykmelding.model.OcrParserPasient
import no.nav.tsm.sykmelding.model.OcrParserPrognose
import no.nav.tsm.sykmelding.model.OcrParserSykmelding
import no.nav.tsm.sykmelding.model.OcrParserTilbakedatering
import no.nav.tsm.sykmelding.model.OcrParserTiltak
import no.nav.tsm.sykmelding.model.OcrValue
import no.nav.tsm.sykmelding.model.valueOrNull
import no.nav.tsm.sykmelding.ocr.Ocr
import no.nav.tsm.sykmelding.ocr.OcrResult
import no.nav.tsm.sykmelding.ocr.ParseSykmeldingOcr

class OcrService(private val papirsykmeldingOcr: ParseSykmeldingOcr = ParseSykmeldingOcr(Ocr())) {

    suspend fun tryPapirsykmeldingOcr(
        dokuments: Map<DokumentInfo, ByteArray>,
        loggingMeta: LoggingMeta,
    ): Skanningmetadata? {
        val pdfDocuments = dokuments.entries.filter { it.key.filtype.lowercase() == "pdf" }

        if (pdfDocuments.size != 1) {
            log.info("Expected one pdf, found ${pdfDocuments.size} ${fields(loggingMeta)}")
            return null
        }

        val parsed =
            when (val ocrResult = papirsykmeldingOcr.doOcr(pdfDocuments.first().value)) {
                is OcrResult.Parsed -> ocrResult.sykmelding
                OcrResult.NoMatch -> {
                    log.info("No ocr template match ${fields(loggingMeta)}")
                    return null
                }

                is OcrResult.TooManyPages -> {
                    log.info("Too many pages for ocr: ${ocrResult.pages} ${fields(loggingMeta)}")
                    return null
                }

                is OcrResult.Failed -> {
                    log.warn(
                        "Ocr failed: ${ocrResult.reason} ${fields(loggingMeta)}",
                        ocrResult.cause,
                    )
                    return null
                }
            }

        return Skanningmetadata().apply {
            sykemeldinger =
                SykemeldingerType().apply {
                    syketilfelleStartDato = parsed.syketilfelletStartDato.valueOrNull()
                    pasient = parsed.pasient.toPasientType()
                    arbeidsgiver = parsed.arbeidsgiver.toArbeidsgiverType()
                    medisinskVurdering = parsed.medisinskVurdering.toMedisinskVurderingType()
                    aktivitet = parsed.aktivitet.toAktivitetType()
                    prognose = parsed.prognose.toPrognoseType()
                    // TODO utdypendeOpplysninger is currently always empty for the current
                    // templates
                    utdypendeOpplysninger = UtdypendeOpplysningerType()
                    tiltak = parsed.tiltak.toTiltakType()
                    meldingTilNAV = parsed.bistandNav.toMeldingTilNavType()
                    meldingTilArbeidsgiver = parsed.meldingTilArbeidsgiver
                    tilbakedatering = parsed.tilbakedatering.toTilbakedateringType()
                    kontaktMedPasient = parsed.toKontaktMedPasient()
                    behandler = parsed.behandler.toBehandlerType()
                }
        }
    }
}

private fun OcrParserBehandler.toBehandlerType(): BehandlerType {
    val parsed = this
    return BehandlerType().apply {
        this.hpr = parsed.hpr.valueOrNull()?.toBigInteger()
        this.adresse = parsed.adresse
        this.telefon = parsed.telefon.valueOrNull()?.toBigInteger()
    }
}

private fun OcrParserSykmelding.toKontaktMedPasient(): KontaktMedPasientType {
    val tilbakedateringDato = tilbakedatering.kontaktDato.valueOrNull()
    val signaturDato = this.behandler.behandletDato?.valueOrNull()

    return KontaktMedPasientType().apply {
        this.behandletDato = signaturDato ?: tilbakedateringDato
    }
}

private fun OcrParserTilbakedatering.toTilbakedateringType(): TilbakedateringType {
    val parsed = this
    return TilbakedateringType().apply {
        this.tilbakeDato = parsed.kontaktDato.valueOrNull()
        this.tilbakebegrunnelse = parsed.begrunnelse
    }
}

private fun OcrParserBistandNav.toMeldingTilNavType(): MeldingTilNAVType {
    val parsed = this
    return MeldingTilNAVType().apply {
        this.isBistandNAVUmiddelbart = parsed.bistandUmiddelbart.toBoolUncertainTrue()
        this.beskrivBistandNAV = parsed.beskrivBistand
    }
}

private fun OcrParserTiltak.toTiltakType(): TiltakType {
    val parsed = this
    return TiltakType().apply {
        tiltakArbeidsplassen = parsed.tiltakArbeidsplassen
        andreTiltak = parsed.andreTiltak
        tiltakNAV = parsed.tiltakNav
    }
}

private fun OcrParserPrognose.toPrognoseType(): PrognoseType {
    val parsed = this

    return PrognoseType().apply {
        friskmelding =
            FriskmeldingType().apply {
                this.isArbeidsforEtterEndtPeriode =
                    parsed.arbeidsforEtterPeriode.toBoolUncertainTrue()
                this.beskrivHensynArbeidsplassen = hensynArbeidsplassen
            }
    }
}

private fun List<OcrParserAktivitet>.toAktivitetType(): AktivitetType {
    val aktivitetList = this
    return AktivitetType().apply {
        val aktivitetType = this
        this.aktivitetIkkeMulig =
            aktivitetList.filterIsInstance<OcrParserAktivitet.IkkeMulig>().singleOrNull()?.let {
                AktivitetIkkeMuligType().apply {
                    this.periodeFOMDato = it.fom.valueOrNull()
                    this.periodeTOMDato = it.tom.valueOrNull()
                    val isArbeidsrelatertArsak = it.arbeidsrelatertArsak.toBoolUncertainTrue()
                    if (isArbeidsrelatertArsak) {
                        this.arbeidsplassen =
                            ArbeidsplassenType().apply { this.isArbeidsplassenHindrer = true }
                    }
                    val isMedisinskeArsaker = it.medisinskArsak.toBoolUncertainTrue()
                    if (isMedisinskeArsaker) {
                        this.medisinskeArsaker =
                            MedisinskeArsakerType().apply { this.isMedArsakerHindrer = true }
                    }
                }
            }

        this.avventendeSykmelding =
            aktivitetList.filterIsInstance<OcrParserAktivitet.Avventende>().singleOrNull()?.let {
                AvventendeSykmeldingType().apply {
                    this.periodeFOMDato = it.fom.valueOrNull()
                    this.periodeTOMDato = it.tom.valueOrNull()
                    aktivitetType.innspillTilArbeidsgiver = it.innspillTilArbeidsgiver
                }
            }

        this.behandlingsdager =
            aktivitetList
                .filterIsInstance<OcrParserAktivitet.Behandlingsdager>()
                .singleOrNull()
                ?.let {
                    BehandlingsdagerType().apply {
                        this.periodeFOMDato = it.fom.valueOrNull()
                        this.periodeTOMDato = it.tom.valueOrNull()
                        this.antallBehandlingsdager =
                            (it.antallBehandlingsdager.valueOrNull() ?: 1).toBigInteger()
                    }
                }

        this.gradertSykmelding =
            aktivitetList.filterIsInstance<OcrParserAktivitet.Gradert>().singleOrNull()?.let {
                GradertSykmeldingType().apply {
                    this.periodeFOMDato = it.fom.valueOrNull()
                    this.periodeTOMDato = it.tom.valueOrNull()
                    this.isReisetilskudd = it.reisetilskudd.toBoolUncertainTrue()
                    this.sykmeldingsgrad = it.grad.valueOrNull()?.toString()
                }
            }

        this.reisetilskudd =
            aktivitetList.filterIsInstance<OcrParserAktivitet.Reisetilskudd>().singleOrNull()?.let {
                ReisetilskuddType().apply {
                    this.periodeFOMDato = it.fom.valueOrNull()
                    this.periodeTOMDato = it.tom.valueOrNull()
                }
            }
    }
}

private fun OcrParserMedisinskVurdering.toMedisinskVurderingType(): MedisinskVurderingType {
    val parsed = this
    return MedisinskVurderingType().apply {
        this.annenFraversArsak =
            parsed.annenFraversArsak
                ?.arsak
                ?.firstOrNull()
                ?.name // TODO map to correct enum in mapping to fellesformat
        this.fraversBeskrivelse = parsed.annenFraversArsak?.beskrivelse
        this.isSvangerskap =
            when (parsed.svangerskap) {
                CheckboxState.CHECKED -> true
                else -> false
            }
        this.isSkjermesForPasient = parsed.skjermetForPasient.toBoolUncertainTrue()
        this.isYrkesskade = parsed.yrkesskade.toBoolUncertainTrue()
        this.yrkesskadedato = parsed.yrkesskadeDato.valueOrNull()
        this.bidiagnose.addAll(parsed.biDiagnoser.toBidagnoseType())
        this.hovedDiagnose.addAll(parsed.hovedDiagnose.toHovedDiagnoseType())
    }
}

private fun CheckboxState.toBoolUncertainTrue(): Boolean =
    when (this) {
        CheckboxState.CHECKED -> true
        CheckboxState.UNCERTAIN -> true
        CheckboxState.UNCHECKED -> false
    }

private fun OcrParserDiagnose?.toHovedDiagnoseType(): List<HovedDiagnoseType> {
    val parsed = this?.diagnose?.valueOrNull() ?: return emptyList()

    return listOf(
        HovedDiagnoseType().apply {
            this.diagnosekodeSystem = parsed.system.name
            this.diagnosekode = parsed.code
            this.diagnose = parsed.text
        }
    )
}

private fun List<OcrParserDiagnose>.toBidagnoseType(): List<BidiagnoseType> {
    return map {
        BidiagnoseType().apply {
            diagnose = it.diagnose.valueOrNull()?.text
            diagnosekode = it.diagnose.valueOrNull()?.code
            diagnosekodeSystem = it.diagnose.valueOrNull()?.system?.name
        }
    }
}

private fun OcrParserArbeidsgiver.toArbeidsgiverType(): ArbeidsgiverType {
    val ocrArbeidsgiver = this
    return ArbeidsgiverType().apply {
        this.harArbeidsgiver =
            when (val arbeidsgiver = ocrArbeidsgiver.harArbeidsgiver) {
                is OcrValue.Read<HarArbeidsgiver> ->
                    when (arbeidsgiver.value) {
                        HarArbeidsgiver.EN_ARBEIDSGIVER -> "En"
                        HarArbeidsgiver.FLERE_ARBEIDSGIVERE -> "Flere"
                        HarArbeidsgiver.INGEN_ARBEIDSGIVER -> "Ingen"
                    }

                is OcrValue.Unreadable -> "En"
                null -> "ingen"
            }
        this.navnArbeidsgiver = ocrArbeidsgiver.navn
        this.stillingsprosent = ocrArbeidsgiver.stillingsprosent.valueOrNull()?.toBigInteger()
        this.yrkesbetegnelse = ocrArbeidsgiver.yrkesbetegnelse
    }
}

private fun OcrParserPasient.toPasientType(): PasientType {
    val ocrPasient = this
    return PasientType().apply {
        val tempFnr = getCrossCheckedValue(ocrPasient.fnr)
        if (tempFnr != null) {
            fnr = tempFnr
        }
    }
}

private fun getCrossCheckedValue(ocrCrossCheck: OcrCrossChecked<String>): String? =
    when (val ocrValue = ocrCrossCheck.value) {
        is OcrValue.Read<String> -> {
            if (ocrCrossCheck.readings.size == ocrCrossCheck.confirmations) {
                ocrValue.value
            } else {
                null
            }
        }

        else -> null
    }
