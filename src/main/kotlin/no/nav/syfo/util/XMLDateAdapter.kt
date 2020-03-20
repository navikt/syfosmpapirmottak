package no.nav.syfo.util

import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.xml.bind.DatatypeConverter

class XMLDateAdapter : LocalDateXmlAdapter() {
    override fun unmarshal(stringValue: String?): LocalDate? = when (stringValue) {
        null -> null
        else -> DatatypeConverter.parseDate(stringValue).toInstant().atZone(ZoneOffset.MAX).toLocalDate()
    }
}

class XMLDateTimeAdapter : LocalDateTimeXmlAdapter() {
    override fun unmarshal(stringValue: String?): LocalDateTime? = when (stringValue) {
        null -> null
        else -> DatatypeConverter.parseDateTime(stringValue).toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
    }
}
