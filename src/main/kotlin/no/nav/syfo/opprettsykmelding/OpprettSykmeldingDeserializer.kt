package no.nav.syfo.opprettsykmelding

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.opprettsykmelding.model.OpprettSykmeldingRecord
import org.apache.kafka.common.serialization.Deserializer

class OpprettSykmeldingDeserializer : Deserializer<OpprettSykmeldingRecord> {
    private val objectMapper =
        jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

    override fun deserialize(topic: String, data: ByteArray): OpprettSykmeldingRecord {
        return objectMapper.readValue(data)
    }
}
