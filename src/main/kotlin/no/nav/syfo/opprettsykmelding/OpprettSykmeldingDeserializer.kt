package no.nav.syfo.opprettsykmelding

import no.nav.syfo.opprettsykmelding.model.OpprettSykmeldingRecord
import org.apache.kafka.common.serialization.Deserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue

class OpprettSykmeldingDeserializer : Deserializer<OpprettSykmeldingRecord> {
    val jsonMapper: JsonMapper = jacksonMapperBuilder().build()

    override fun deserialize(topic: String, data: ByteArray): OpprettSykmeldingRecord {
        return jsonMapper.readValue(data)
    }
}
