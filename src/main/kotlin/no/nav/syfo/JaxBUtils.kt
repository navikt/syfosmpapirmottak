package no.nav.syfo

import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import no.nav.helse.sykSkanningMeta.SkanningmetadataType
import javax.xml.bind.JAXBContext
import javax.xml.bind.Unmarshaller

val jaxbContext: JAXBContext = JAXBContext.newInstance(SkanningmetadataType::class.java)
val skanningMetadataUnmarshaller: Unmarshaller = jaxbContext.createUnmarshaller().apply {
    setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
}
