package no.nav.syfo.service

import java.math.BigInteger
import java.time.LocalDate
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
import no.nav.helse.papirsykemelding.MedArbeidsgiverType
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
import no.nav.helse.papirsykemelding.UtenArbeidsgiverType

data class OcrAvvik(val felt: String, val ocrFil: String?, val tsmOcr: String?)

/**
 * Compares two [Skanningmetadata] field by field and returns the fields that differ together with
 * both values. Blank strings are treated as null. Values may contain personal data (e.g. fnr), so
 * only [OcrAvvik.felt] should be logged.
 */
fun sammenlignSkanningmetadata(
    ocrFil: Skanningmetadata,
    tsmSkanningmetadata: Skanningmetadata,
): List<OcrAvvik> {
    val avvik = SkanningmetadataAvvik()
    avvik.sykemeldinger(ocrFil.sykemeldinger, tsmSkanningmetadata.sykemeldinger)
    return avvik.felter
}

private class SkanningmetadataAvvik {
    val felter = mutableListOf<OcrAvvik>()

    fun sykemeldinger(a: SykemeldingerType?, b: SykemeldingerType?) {
        felt("syketilfelleStartDato", a?.syketilfelleStartDato, b?.syketilfelleStartDato)
        pasient(a?.pasient, b?.pasient)
        arbeidsgiver(a?.arbeidsgiver, b?.arbeidsgiver)
        medisinskVurdering(a?.medisinskVurdering, b?.medisinskVurdering)
        aktivitet(a?.aktivitet, b?.aktivitet)
        prognose(a?.prognose, b?.prognose)
        utdypendeOpplysninger(a?.utdypendeOpplysninger, b?.utdypendeOpplysninger)
        tiltak(a?.tiltak, b?.tiltak)
        meldingTilNav(a?.meldingTilNAV, b?.meldingTilNAV)
        felt("meldingTilArbeidsgiver", a?.meldingTilArbeidsgiver, b?.meldingTilArbeidsgiver)
        tilbakedatering(a?.tilbakedatering, b?.tilbakedatering)
        kontaktMedPasient(a?.kontaktMedPasient, b?.kontaktMedPasient)
        behandler(a?.behandler, b?.behandler)
    }

    private fun pasient(a: PasientType?, b: PasientType?) {
        felt("pasient.fnr", a?.fnr, b?.fnr)
    }

    private fun arbeidsgiver(a: ArbeidsgiverType?, b: ArbeidsgiverType?) {
        felt("arbeidsgiver.harArbeidsgiver", a?.harArbeidsgiver, b?.harArbeidsgiver)
        felt("arbeidsgiver.navnArbeidsgiver", a?.navnArbeidsgiver, b?.navnArbeidsgiver)
        felt("arbeidsgiver.yrkesbetegnelse", a?.yrkesbetegnelse, b?.yrkesbetegnelse)
        felt("arbeidsgiver.stillingsprosent", a?.stillingsprosent, b?.stillingsprosent)
    }

    private fun medisinskVurdering(a: MedisinskVurderingType?, b: MedisinskVurderingType?) {
        val prefix = "medisinskVurdering"
        hovedDiagnoser(a?.hovedDiagnose.orEmpty(), b?.hovedDiagnose.orEmpty())
        biDiagnoser(a?.bidiagnose.orEmpty(), b?.bidiagnose.orEmpty())
        felt("$prefix.annenFraversArsak", a?.annenFraversArsak, b?.annenFraversArsak)
        felt("$prefix.fraversBeskrivelse", a?.fraversBeskrivelse, b?.fraversBeskrivelse)
        felt("$prefix.svangerskap", a?.isSvangerskap, b?.isSvangerskap)
        felt("$prefix.yrkesskade", a?.isYrkesskade, b?.isYrkesskade)
        felt("$prefix.yrkesskadedato", a?.yrkesskadedato, b?.yrkesskadedato)
        felt("$prefix.skjermesForPasient", a?.isSkjermesForPasient, b?.isSkjermesForPasient)
    }

    private fun hovedDiagnoser(a: List<HovedDiagnoseType>, b: List<HovedDiagnoseType>) {
        val prefix = "medisinskVurdering.hovedDiagnose"
        felt("$prefix.antall", a.size, b.size)
        a.zip(b).forEach { (da, db) ->
            felt("$prefix.diagnosekodeSystem", da.diagnosekodeSystem, db.diagnosekodeSystem)
            felt("$prefix.diagnosekode", da.diagnosekode, db.diagnosekode)
            felt("$prefix.diagnose", da.diagnose, db.diagnose)
        }
    }

    private fun biDiagnoser(a: List<BidiagnoseType>, b: List<BidiagnoseType>) {
        val prefix = "medisinskVurdering.bidiagnose"
        felt("$prefix.antall", a.size, b.size)
        a.zip(b).forEach { (da, db) ->
            felt("$prefix.diagnosekodeSystem", da.diagnosekodeSystem, db.diagnosekodeSystem)
            felt("$prefix.diagnosekode", da.diagnosekode, db.diagnosekode)
            felt("$prefix.diagnose", da.diagnose, db.diagnose)
        }
    }

    private fun aktivitet(a: AktivitetType?, b: AktivitetType?) {
        felt(
            "aktivitet.innspillTilArbeidsgiver",
            a?.innspillTilArbeidsgiver,
            b?.innspillTilArbeidsgiver,
        )
        avventende(a?.avventendeSykmelding, b?.avventendeSykmelding)
        gradert(a?.gradertSykmelding, b?.gradertSykmelding)
        aktivitetIkkeMulig(a?.aktivitetIkkeMulig, b?.aktivitetIkkeMulig)
        behandlingsdager(a?.behandlingsdager, b?.behandlingsdager)
        reisetilskudd(a?.reisetilskudd, b?.reisetilskudd)
    }

    private fun avventende(a: AvventendeSykmeldingType?, b: AvventendeSykmeldingType?) {
        val prefix = "aktivitet.avventendeSykmelding"
        felt(prefix, a != null, b != null)
        felt("$prefix.periodeFOMDato", a?.periodeFOMDato, b?.periodeFOMDato)
        felt("$prefix.periodeTOMDato", a?.periodeTOMDato, b?.periodeTOMDato)
    }

    private fun gradert(a: GradertSykmeldingType?, b: GradertSykmeldingType?) {
        val prefix = "aktivitet.gradertSykmelding"
        felt(prefix, a != null, b != null)
        felt("$prefix.periodeFOMDato", a?.periodeFOMDato, b?.periodeFOMDato)
        felt("$prefix.periodeTOMDato", a?.periodeTOMDato, b?.periodeTOMDato)
        felt("$prefix.sykmeldingsgrad", a?.sykmeldingsgrad, b?.sykmeldingsgrad)
        felt("$prefix.reisetilskudd", a?.isReisetilskudd, b?.isReisetilskudd)
    }

    private fun aktivitetIkkeMulig(a: AktivitetIkkeMuligType?, b: AktivitetIkkeMuligType?) {
        val prefix = "aktivitet.aktivitetIkkeMulig"
        felt(prefix, a != null, b != null)
        felt("$prefix.periodeFOMDato", a?.periodeFOMDato, b?.periodeFOMDato)
        felt("$prefix.periodeTOMDato", a?.periodeTOMDato, b?.periodeTOMDato)
        medisinskeArsaker(a?.medisinskeArsaker, b?.medisinskeArsaker)
        arbeidsplassen(a?.arbeidsplassen, b?.arbeidsplassen)
    }

    private fun medisinskeArsaker(a: MedisinskeArsakerType?, b: MedisinskeArsakerType?) {
        val prefix = "aktivitet.aktivitetIkkeMulig.medisinskeArsaker"
        felt("$prefix.medArsakerHindrer", a?.isMedArsakerHindrer, b?.isMedArsakerHindrer)
        felt("$prefix.medArsakerBesk", a?.medArsakerBesk, b?.medArsakerBesk)
    }

    private fun arbeidsplassen(a: ArbeidsplassenType?, b: ArbeidsplassenType?) {
        val prefix = "aktivitet.aktivitetIkkeMulig.arbeidsplassen"
        felt(
            "$prefix.arbeidsplassenHindrer",
            a?.isArbeidsplassenHindrer,
            b?.isArbeidsplassenHindrer,
        )
        felt("$prefix.arbeidsplassenBesk", a?.arbeidsplassenBesk, b?.arbeidsplassenBesk)
    }

    private fun behandlingsdager(a: BehandlingsdagerType?, b: BehandlingsdagerType?) {
        val prefix = "aktivitet.behandlingsdager"
        felt(prefix, a != null, b != null)
        felt("$prefix.periodeFOMDato", a?.periodeFOMDato, b?.periodeFOMDato)
        felt("$prefix.periodeTOMDato", a?.periodeTOMDato, b?.periodeTOMDato)
        felt("$prefix.antallBehandlingsdager", a?.antallBehandlingsdager, b?.antallBehandlingsdager)
    }

    private fun reisetilskudd(a: ReisetilskuddType?, b: ReisetilskuddType?) {
        val prefix = "aktivitet.reisetilskudd"
        felt(prefix, a != null, b != null)
        felt("$prefix.periodeFOMDato", a?.periodeFOMDato, b?.periodeFOMDato)
        felt("$prefix.periodeTOMDato", a?.periodeTOMDato, b?.periodeTOMDato)
    }

    private fun prognose(a: PrognoseType?, b: PrognoseType?) {
        friskmelding(a?.friskmelding, b?.friskmelding)
        medArbeidsgiver(a?.medArbeidsgiver, b?.medArbeidsgiver)
        utenArbeidsgiver(a?.utenArbeidsgiver, b?.utenArbeidsgiver)
    }

    private fun friskmelding(a: FriskmeldingType?, b: FriskmeldingType?) {
        val prefix = "prognose.friskmelding"
        felt(
            "$prefix.arbeidsforEtterEndtPeriode",
            a?.isArbeidsforEtterEndtPeriode,
            b?.isArbeidsforEtterEndtPeriode,
        )
        felt(
            "$prefix.beskrivHensynArbeidsplassen",
            a?.beskrivHensynArbeidsplassen,
            b?.beskrivHensynArbeidsplassen,
        )
    }

    private fun medArbeidsgiver(a: MedArbeidsgiverType?, b: MedArbeidsgiverType?) {
        val prefix = "prognose.medArbeidsgiver"
        felt(
            "$prefix.tilbakeSammeArbeidsgiver",
            a?.isTilbakeSammeArbeidsgiver,
            b?.isTilbakeSammeArbeidsgiver,
        )
        felt("$prefix.tilbakeDato", a?.tilbakeDato, b?.tilbakeDato)
        felt(
            "$prefix.tilbakeAnnenArbeidsgiver",
            a?.isTilbakeAnnenArbeidsgiver,
            b?.isTilbakeAnnenArbeidsgiver,
        )
        felt("$prefix.datoNyTilbakemelding", a?.datoNyTilbakemelding, b?.datoNyTilbakemelding)
    }

    private fun utenArbeidsgiver(a: UtenArbeidsgiverType?, b: UtenArbeidsgiverType?) {
        val prefix = "prognose.utenArbeidsgiver"
        felt("$prefix.tilbakeArbeid", a?.isTilbakeArbeid, b?.isTilbakeArbeid)
        felt("$prefix.tilbakeDato", a?.tilbakeDato, b?.tilbakeDato)
        felt("$prefix.datoNyTilbakemelding", a?.datoNyTilbakemelding, b?.datoNyTilbakemelding)
    }

    private fun utdypendeOpplysninger(
        a: UtdypendeOpplysningerType?,
        b: UtdypendeOpplysningerType?,
    ) {
        val prefix = "utdypendeOpplysninger"
        felt("$prefix.sykehistorie", a?.sykehistorie, b?.sykehistorie)
        felt("$prefix.arbeidsevne", a?.arbeidsevne, b?.arbeidsevne)
        felt("$prefix.behandlingsresultat", a?.behandlingsresultat, b?.behandlingsresultat)
        felt("$prefix.planlagtBehandling", a?.planlagtBehandling, b?.planlagtBehandling)
    }

    private fun tiltak(a: TiltakType?, b: TiltakType?) {
        felt("tiltak.tiltakArbeidsplassen", a?.tiltakArbeidsplassen, b?.tiltakArbeidsplassen)
        felt("tiltak.tiltakNAV", a?.tiltakNAV, b?.tiltakNAV)
        felt("tiltak.andreTiltak", a?.andreTiltak, b?.andreTiltak)
    }

    private fun meldingTilNav(a: MeldingTilNAVType?, b: MeldingTilNAVType?) {
        felt(
            "meldingTilNAV.bistandNAVUmiddelbart",
            a?.isBistandNAVUmiddelbart,
            b?.isBistandNAVUmiddelbart,
        )
        felt("meldingTilNAV.beskrivBistandNAV", a?.beskrivBistandNAV, b?.beskrivBistandNAV)
    }

    private fun tilbakedatering(a: TilbakedateringType?, b: TilbakedateringType?) {
        felt("tilbakedatering.tilbakeDato", a?.tilbakeDato, b?.tilbakeDato)
        felt("tilbakedatering.tilbakebegrunnelse", a?.tilbakebegrunnelse, b?.tilbakebegrunnelse)
    }

    private fun kontaktMedPasient(a: KontaktMedPasientType?, b: KontaktMedPasientType?) {
        felt("kontaktMedPasient.behandletDato", a?.behandletDato, b?.behandletDato)
    }

    private fun behandler(a: BehandlerType?, b: BehandlerType?) {
        felt("behandler.hpr", a?.hpr, b?.hpr)
        felt("behandler.telefon", a?.telefon, b?.telefon)
        felt("behandler.adresse", a?.adresse, b?.adresse)
    }

    private fun felt(navn: String, a: String?, b: String?) {
        val verdiA = a?.trim()?.ifEmpty { null }
        val verdiB = b?.trim()?.ifEmpty { null }
        if (verdiA != verdiB) felter.add(OcrAvvik(navn, verdiA, verdiB))
    }

    private fun felt(navn: String, a: LocalDate?, b: LocalDate?) = leggTilHvisUlik(navn, a, b)

    private fun felt(navn: String, a: BigInteger?, b: BigInteger?) = leggTilHvisUlik(navn, a, b)

    private fun felt(navn: String, a: Boolean?, b: Boolean?) = leggTilHvisUlik(navn, a, b)

    private fun felt(navn: String, a: Int, b: Int) = leggTilHvisUlik(navn, a, b)

    private fun <T : Comparable<T>> leggTilHvisUlik(navn: String, a: T?, b: T?) {
        if (a != b) felter.add(OcrAvvik(navn, a?.toString(), b?.toString()))
    }
}
