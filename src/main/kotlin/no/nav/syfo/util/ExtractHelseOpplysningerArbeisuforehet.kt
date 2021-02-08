package no.nav.syfo.util

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import java.time.LocalDateTime
import java.time.LocalTime

fun extractSyketilfelleStartDato(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): LocalDateTime =
    LocalDateTime.of(helseOpplysningerArbeidsuforhet.syketilfelleStartDato, LocalTime.NOON)
