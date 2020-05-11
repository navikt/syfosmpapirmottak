package no.nav.syfo.service

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.util.LoggingMeta
import java.time.LocalDateTime

fun mapOcrFilTilPapirSmRegistrering(
        journalpostId: String,
        fnr: String?,
        aktorId: String?,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        sykmeldingId: String,
        ocrFil: Skanningmetadata?,
        loggingMeta: LoggingMeta
): PapirSmRegistering {


    val sykmelding = ocrFil?.sykemeldinger

    return PapirSmRegistering(journalpostId, fnr, aktorId, dokumentInfoId, datoOpprettet, sykmeldingId,
            syketilfelleStartDato = sykmelding?.syketilfelleStartDato,
            arbeidsgiver = tilArbeidsgiver(arbeidsgiverType = sykmelding?.arbeidsgiver, loggingMeta = loggingMeta),
            medisinskVurdering = sykmelding?.medisinskVurdering?.let { tilMedisinskVurdering(medisinskVurderingType = it, loggingMeta = loggingMeta ) },
            aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                periode.apply { sykmelding?.aktivitet?.let { tilPeriodeListe(it) } }
            },
            prognose = sykmelding?.prognose?.let { tilPrognose(prognoseType = it) },
            utdypendeOpplysninger = tilUtdypendeOpplysninger(utdypendeOpplysningerType = sykmelding?.utdypendeOpplysninger),
            tiltak = HelseOpplysningerArbeidsuforhet.Tiltak().apply {
                tiltakArbeidsplassen = sykmelding?.tiltak?.tiltakArbeidsplassen
                tiltakNAV = sykmelding?.tiltak?.tiltakNAV
                andreTiltak = sykmelding?.tiltak?.andreTiltak
            },
            meldingTilNav = HelseOpplysningerArbeidsuforhet.MeldingTilNav().apply {
                beskrivBistandNAV.apply {  sykmelding?.meldingTilNAV?.beskrivBistandNAV }
                isBistandNAVUmiddelbart.apply { sykmelding?.meldingTilNAV?.isBistandNAVUmiddelbart }
},
            meldingTilArbeidsgiver = sykmelding?.meldingTilArbeidsgiver,
            tilbakedateringDato = sykmelding?.tilbakedatering?.tilbakeDato,
            tilbakedateringBegrunnelse = sykmelding?.tilbakedatering?.tilbakebegrunnelse,
            behandletDato = sykmelding?.kontaktMedPasient?.behandletDato,
            behandlerHpr = sykmelding?.behandler?.hpr,
            behandlerAdresse = sykmelding?.behandler?.adresse,
            behandlerTelefon = sykmelding?.behandler?.telefon

    )

}