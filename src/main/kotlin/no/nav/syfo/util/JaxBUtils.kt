package no.nav.syfo.util

import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller

val journalfoeringHendelseRecordJaxBContext: JAXBContext = JAXBContext.newInstance(JournalfoeringHendelseRecord::class.java)
val journalfoeringHendelseRecordMarshaller: Marshaller = journalfoeringHendelseRecordJaxBContext.createMarshaller()

val sykemeldingerTypeJaxBContext: JAXBContext = JAXBContext.newInstance(SykemeldingerType::class.java)
val sykemeldingerTypeUnmarshaller: Unmarshaller = sykemeldingerTypeJaxBContext.createUnmarshaller()