package no.nav.syfo.util

import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Unmarshaller
import no.nav.helse.sykSkanningMeta.Skanningmetadata

val jaxbContext: JAXBContext = JAXBContext.newInstance(Skanningmetadata::class.java)
val skanningMetadataUnmarshaller: Unmarshaller = jaxbContext.createUnmarshaller().apply {
    setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
}
