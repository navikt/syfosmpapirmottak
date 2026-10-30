package no.nav.syfo.util

import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Marshaller.JAXB_ENCODING
import javax.xml.bind.Unmarshaller
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.Source
import javax.xml.transform.sax.SAXSource
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import org.xml.sax.InputSource

val jaxbContext: JAXBContext = JAXBContext.newInstance(Skanningmetadata::class.java)
val skanningMetadataUnmarshaller: Unmarshaller =
    jaxbContext.createUnmarshaller().apply {
        setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
    }

val fellesformatMarshaller: Marshaller =
    JAXBContext.newInstance(
            XMLEIFellesformat::class.java,
            XMLMsgHead::class.java,
            HelseOpplysningerArbeidsuforhet::class.java,
        )
        .createMarshaller()
        .apply { setProperty(JAXB_ENCODING, "UTF-8") }

fun Marshaller.toString(input: Any): String =
    StringWriter().use {
        marshal(input, it)
        it.toString()
    }

fun safeUnmarshalSkanningmetadata(inputMessageText: ByteArrayInputStream): Skanningmetadata {
    // Disable XXE
    val spf: SAXParserFactory = SAXParserFactory.newInstance()
    spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    spf.isNamespaceAware = true

    val xmlSource: Source = SAXSource(spf.newSAXParser().xmlReader, InputSource(inputMessageText))

    return skanningMetadataUnmarshaller.unmarshal(xmlSource) as Skanningmetadata
}
