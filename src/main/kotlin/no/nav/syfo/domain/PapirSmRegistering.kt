package no.nav.syfo.domain

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime

data class PapirSmRegistering(
        val journalpostId: String,
        val fnr: String?,
        val aktorId: String?,
        val dokumentInfoId: String?,
        val datoOpprettet: LocalDateTime?,
        val sykmeldingId: String,
        val syketilfelleStartDato: LocalDate?,
        val arbeidsgiver: HelseOpplysningerArbeidsuforhet.Arbeidsgiver?,
        val medisinskVurdering: HelseOpplysningerArbeidsuforhet.MedisinskVurdering?,
        val aktivitet: HelseOpplysningerArbeidsuforhet.Aktivitet?,
        val prognose: HelseOpplysningerArbeidsuforhet.Prognose?,
        val utdypendeOpplysninger: HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger?,
        val tiltak: HelseOpplysningerArbeidsuforhet.Tiltak?,
        val meldingTilNav: HelseOpplysningerArbeidsuforhet.MeldingTilNav?,
        val meldingTilArbeidsgiver: String?,
        val tilbakedateringDato: LocalDate?,
        val tilbakedateringBegrunnelse: String?,
        val behandletDato: LocalDate?,
        val behandlerHpr: BigInteger?,
        val behandlerAdresse: String?,
        val behandlerTelefon: BigInteger?

)