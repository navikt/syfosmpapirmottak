package no.nav.syfo.util

import java.time.LocalDateTime
import java.time.LocalTime
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet

fun extractSyketilfelleStartDato(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): LocalDateTime =
        LocalDateTime.of(helseOpplysningerArbeidsuforhet.syketilfelleStartDato, LocalTime.NOON)
