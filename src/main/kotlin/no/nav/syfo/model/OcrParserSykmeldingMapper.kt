package no.nav.syfo.model

import no.nav.sykmelding.api.Aktivitet
import no.nav.sykmelding.api.Sykmelding

/**
 * Maps the published library model [Sykmelding] (`no.nav.sykmelding.api`) to the local
 * [OcrParserSykmelding] used by [no.nav.syfo.service.OcrParserImCompareService].
 *
 * The two models are 1:1 field-compatible; the local model is kept so the shadow-compare logic (and
 * its tests) stay untouched while the transport changes from HTTP to in-process.
 */
fun Sykmelding.toOcrParserSykmelding(): OcrParserSykmelding =
    OcrParserSykmelding(
        formType = formType,
        formVersion = formVersion,
        vurderingType = vurderingType?.let { OcrParserVurderingType.valueOf(it.name) },
        legemeldtFravaerStart = legemeldtFravaerStart,
        pasient =
            OcrParserPasient(
                navn = pasient.navn,
                etternavn = pasient.etternavn,
                fornavn = pasient.fornavn,
                fnr = pasient.fnr,
                telefon = pasient.telefon,
                adresse = pasient.adresse,
                postnrSted = pasient.postnrSted,
                fastlege = pasient.fastlege,
                navKontor = pasient.navKontor,
            ),
        arbeidsgiver =
            OcrParserArbeidsgiver(
                navn = arbeidsgiver.navn,
                yrkeStilling = arbeidsgiver.yrkeStilling,
                stillingsprosent = arbeidsgiver.stillingsprosent,
                flereArbeidsgivere = arbeidsgiver.flereArbeidsgivere,
            ),
        diagnose =
            OcrParserDiagnose(
                hoveddiagnoseKodesystem = diagnose.hoveddiagnoseKodesystem,
                hoveddiagnoseKode = diagnose.hoveddiagnoseKode,
                hoveddiagnoseTekst = diagnose.hoveddiagnoseTekst,
                bidiagnoser =
                    diagnose.bidiagnoser.map {
                        Bidiagnose(kodesystem = it.kodesystem, kode = it.kode, tekst = it.tekst)
                    },
                annenFravarsarsak = diagnose.annenFravarsarsak,
                svangerskap = diagnose.svangerskap,
                yrkesskade = diagnose.yrkesskade,
                skadedato = diagnose.skadedato,
                skjermingPaakrevet = diagnose.skjermingPaakrevet,
            ),
        aktiviteter = aktiviteter.map { it.toOcrParserAktivitet() },
        prognose = OcrParserPrognose(arbeidsforEtterPeriode = prognose.arbeidsforEtterPeriode),
        tilleggsinformasjon =
            OcrParserTilleggsinformasjon(bistandNavOnskes = tilleggsinformasjon.bistandNavOnskes),
        tilbakedatering =
            OcrParserTilbakedatering(
                kontaktDato = tilbakedatering.kontaktDato,
                beskrivelse = tilbakedatering.beskrivelse,
            ),
        sykmelder =
            OcrParserSykmelder(
                dato = sykmelder.dato,
                navn = sykmelder.navn,
                hprNummer = sykmelder.hprNummer,
                adresse = sykmelder.adresse,
                telefon = sykmelder.telefon,
            ),
        meta =
            OcrParserSykmeldingMeta(
                formId = meta.formId,
                formVersion = meta.formVersion,
                parserId = meta.parserId,
                warnings = meta.warnings,
            ),
    )

private fun Aktivitet.toOcrParserAktivitet(): OcrParserAktivitet =
    when (this) {
        is Aktivitet.IkkeMulig ->
            OcrParserAktivitet.IkkeMulig(
                fom = fom,
                tom = tom,
                medisinskArsak = medisinskArsak,
                medisinskArsakType = medisinskArsakType,
                medisinskArsakBeskrivelse = medisinskArsakBeskrivelse,
                arbeidsrelatertArsak = arbeidsrelatertArsak,
            )
        is Aktivitet.Gradert ->
            OcrParserAktivitet.Gradert(
                fom = fom,
                tom = tom,
                grad = grad,
                reisetilskudd = reisetilskudd,
            )
        is Aktivitet.Avventende ->
            OcrParserAktivitet.Avventende(
                fom = fom,
                tom = tom,
                innspillTilArbeidsgiver = innspillTilArbeidsgiver,
            )
        is Aktivitet.Behandlingsdager ->
            OcrParserAktivitet.Behandlingsdager(
                fom = fom,
                tom = tom,
                antallBehandlingsdager = antallBehandlingsdager,
            )
        is Aktivitet.Reisetilskudd -> OcrParserAktivitet.Reisetilskudd(fom = fom, tom = tom)
    }
