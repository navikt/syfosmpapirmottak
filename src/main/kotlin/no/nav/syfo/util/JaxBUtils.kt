package no.nav.syfo.util

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sykSkanningMeta.Skanningmetadata

val jaxbContext: JAXBContext = JAXBContext.newInstance(Skanningmetadata::class.java)
val skanningMetadataUnmarshaller: Unmarshaller = jaxbContext.createUnmarshaller().apply {
    setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
}
val sykmeldingMarshaller: Marshaller = JAXBContext.newInstance(HelseOpplysningerArbeidsuforhet::class.java).createMarshaller()
        .apply { setProperty(Marshaller.JAXB_ENCODING, "UTF-8") }

val xmlObjectWriter: XmlMapper = XmlMapper().apply {
    registerModule(JavaTimeModule())
    registerKotlinModule()
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}
