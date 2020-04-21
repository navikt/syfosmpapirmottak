package no.nav.syfo.domain

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import java.time.LocalDateTime

@JacksonXmlRootElement(localName = "syfo")
data class Syfo(
    val tilleggsdata: Tilleggsdata,
    val sykmelding: String
)

data class Tilleggsdata(
    val ediLoggId: String,
    val sykmeldingId: String,
    val msgId: String,
    val syketilfelleStartDato: LocalDateTime
)
