package no.nav.syfo.util

import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller

val journalfoeringHendelseRecordJaxBContext: JAXBContext = JAXBContext.newInstance(JournalfoeringHendelseRecord::class.java)
val journalfoeringHendelseRecordMarshaller: Marshaller = journalfoeringHendelseRecordJaxBContext.createMarshaller()
