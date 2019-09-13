package no.nav.syfo

import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import javax.xml.bind.JAXBContext
import javax.xml.bind.Unmarshaller

val jaxbContext: JAXBContext = JAXBContext.newInstance(Skanningmetadata::class.java)
val skanningMetadataUnmarshaller: Unmarshaller = jaxbContext.createUnmarshaller().apply {
    setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
}
