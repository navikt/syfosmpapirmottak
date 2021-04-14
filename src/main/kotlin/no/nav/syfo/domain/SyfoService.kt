package no.nav.syfo.domain

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import java.time.LocalDateTime

data class SyfoserviceSykmeldingKafkaMessage(
    val metadata: KafkaMessageMetadata,
    val helseopplysninger: HelseOpplysningerArbeidsuforhet,
    val tilleggsdata: Tilleggsdata
)

data class Tilleggsdata(
    val ediLoggId: String,
    val sykmeldingId: String,
    val msgId: String,
    val syketilfelleStartDato: LocalDateTime
)

data class KafkaMessageMetadata(
    val sykmeldingId: String,
    val source: String
)
