package no.nav.syfo.util

import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller
import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord

val journalfoeringHendelseRecordJaxBContext: JAXBContext = JAXBContext.newInstance(JournalfoeringHendelseRecord::class.java)
val journalfoeringHendelseRecordMarshaller: Marshaller = journalfoeringHendelseRecordJaxBContext.createMarshaller()

val sykemeldingerTypeJaxBContext: JAXBContext = JAXBContext.newInstance(SykemeldingerType::class.java)
val sykemeldingerTypeUnmarshaller: Unmarshaller = sykemeldingerTypeJaxBContext.createUnmarshaller()
