package no.nav.syfo.service

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sykSkanningMeta.AktivitetIkkeMuligType
import no.nav.helse.sykSkanningMeta.AktivitetType
import no.nav.helse.sykSkanningMeta.ArbeidsgiverType
import no.nav.helse.sykSkanningMeta.ArbeidsplassenType
import no.nav.helse.sykSkanningMeta.AvventendeSykmeldingType
import no.nav.helse.sykSkanningMeta.BehandlingsdagerType
import no.nav.helse.sykSkanningMeta.BidiagnoseType
import no.nav.helse.sykSkanningMeta.FriskmeldingType
import no.nav.helse.sykSkanningMeta.GradertSykmeldingType
import no.nav.helse.sykSkanningMeta.HovedDiagnoseType
import no.nav.helse.sykSkanningMeta.MedArbeidsgiverType
import no.nav.helse.sykSkanningMeta.MedisinskVurderingType
import no.nav.helse.sykSkanningMeta.MedisinskeArsakerType
import no.nav.helse.sykSkanningMeta.PrognoseType
import no.nav.helse.sykSkanningMeta.ReisetilskuddType
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.helse.sykSkanningMeta.UtdypendeOpplysningerType
import no.nav.helse.sykSkanningMeta.UtenArbeidsgiverType
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Kode
import no.nav.syfo.client.getFileAsString
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.get
import no.nav.syfo.util.skanningMetadataUnmarshaller
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.amshove.kluent.shouldNotThrow
import org.amshove.kluent.shouldThrow
import java.io.StringReader
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.util.UUID

class FellesformatMapperServiceSpek : FunSpec({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val fnrPasient = "12345678910"
    val aktorId = "aktorId"
    val fnrLege = "fnrLege"
    val aktorIdLege = "aktorIdLege"
    val hprNummer = "10052512"
    val datoOpprettet = LocalDateTime.now()
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")
    val godkjenninger = listOf(Godkjenning(helsepersonellkategori = Kode(true, 1, "LE")))

    context("MappingService ende-til-ende") {
        test("Realistisk case ende-til-ende") {
            val skanningMetadata = skanningMetadataUnmarshaller
                .unmarshal(StringReader(getFileAsString("src/test/resources/ocr-sykmelding.xml")))
                as Skanningmetadata
            val sykmelder = Sykmelder(
                hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = "Fornavn",
                mellomnavn = null, etternavn = "Etternavn", telefonnummer = null, godkjenninger = listOf()
            )
            val pdlPerson = PdlPerson(Navn("fornavn", "mellomnavn", "etternavn"), "12345678910", "aktorid", null)

            val fellesformat = mapOcrFilTilFellesformat(
                skanningmetadata = skanningMetadata,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                loggingMeta = loggingMetadata,
                pdlPerson = pdlPerson,
                journalpostId = journalpostId
            )

            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val msgHead = fellesformat.get<XMLMsgHead>()

            val sykmelding = healthInformation.toSykmelding(
                sykmeldingId = UUID.randomUUID().toString(),
                pasientAktoerId = aktorId,
                legeAktoerId = sykmelder.aktorId,
                msgId = sykmeldingId,
                signaturDato = msgHead.msgInfo.genDate
            )

            val receivedSykmelding = ReceivedSykmelding(
                sykmelding = sykmelding,
                personNrPasient = fnrPasient,
                tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                personNrLege = sykmelder.fnr,
                navLogId = sykmeldingId,
                msgId = sykmeldingId,
                legekontorOrgNr = null,
                legekontorOrgName = "",
                legekontorHerId = null,
                legekontorReshId = null,
                mottattDato = datoOpprettet,
                rulesetVersion = healthInformation.regelSettVersjon,
                fellesformat = objectMapper.writeValueAsString(fellesformat),
                tssid = null,
                merknader = null,
                legeHelsepersonellkategori = godkjenninger.getHelsepersonellKategori(),
                legeHprNr = hprNummer,
                partnerreferanse = null,
                vedlegg = emptyList()
            )

            receivedSykmelding.personNrPasient shouldBeEqualTo fnrPasient
            receivedSykmelding.personNrLege shouldBeEqualTo fnrLege
            receivedSykmelding.navLogId shouldBeEqualTo sykmeldingId
            receivedSykmelding.msgId shouldBeEqualTo sykmeldingId
            receivedSykmelding.legekontorOrgName shouldBeEqualTo ""
            receivedSykmelding.mottattDato shouldBeEqualTo datoOpprettet
            receivedSykmelding.tssid shouldBeEqualTo null
            receivedSykmelding.sykmelding.pasientAktoerId shouldBeEqualTo aktorId
            receivedSykmelding.sykmelding.medisinskVurdering shouldNotBeEqualTo null
            receivedSykmelding.sykmelding.skjermesForPasient shouldBeEqualTo false
            receivedSykmelding.sykmelding.arbeidsgiver shouldNotBeEqualTo null
            receivedSykmelding.sykmelding.perioder.size shouldBeEqualTo 1
            receivedSykmelding.sykmelding.prognose shouldBeEqualTo null
            receivedSykmelding.sykmelding.utdypendeOpplysninger shouldBeEqualTo emptyMap()
            receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldBeEqualTo null
            receivedSykmelding.sykmelding.tiltakNAV shouldBeEqualTo null
            receivedSykmelding.sykmelding.andreTiltak shouldBeEqualTo null
            receivedSykmelding.sykmelding.meldingTilNAV?.bistandUmiddelbart shouldBeEqualTo true
            receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldBeEqualTo null
            receivedSykmelding.sykmelding.kontaktMedPasient shouldBeEqualTo KontaktMedPasient(null, null)
            receivedSykmelding.sykmelding.behandletTidspunkt shouldBeEqualTo LocalDateTime.of(LocalDate.of(2019, Month.AUGUST, 15), LocalTime.NOON)
            receivedSykmelding.sykmelding.behandler shouldNotBeEqualTo null
            receivedSykmelding.sykmelding.avsenderSystem shouldBeEqualTo AvsenderSystem("Papirsykmelding", journalpostId)
            receivedSykmelding.sykmelding.syketilfelleStartDato shouldBeEqualTo LocalDate.of(2019, Month.AUGUST, 15)
            receivedSykmelding.sykmelding.signaturDato shouldBeEqualTo LocalDateTime.of(LocalDate.of(2019, Month.AUGUST, 15), LocalTime.NOON)
            receivedSykmelding.sykmelding.navnFastlege shouldBeEqualTo null
            receivedSykmelding.legeHelsepersonellkategori shouldBeEqualTo "LE"
        }

        test("Minimal ocr-fil") {
            val skanningMetadata = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/minimal-ocr-sykmelding.xml"))) as Skanningmetadata
            val sykmelder = Sykmelder(hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = null, mellomnavn = null, etternavn = null, telefonnummer = null, godkjenninger = listOf())
            val pdlPerson = PdlPerson(Navn("fornavn", "mellomnavn", "etternavn"), "12345678910", "aktorid", null)
            val fellesformat = mapOcrFilTilFellesformat(
                skanningmetadata = skanningMetadata,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                loggingMeta = loggingMetadata,
                pdlPerson = pdlPerson,
                journalpostId = journalpostId
            )

            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val msgHead = fellesformat.get<XMLMsgHead>()

            val sykmelding = healthInformation.toSykmelding(
                sykmeldingId = UUID.randomUUID().toString(),
                pasientAktoerId = aktorId,
                legeAktoerId = sykmelder.aktorId,
                msgId = sykmeldingId,
                signaturDato = msgHead.msgInfo.genDate
            )

            val receivedSykmelding = ReceivedSykmelding(
                sykmelding = sykmelding,
                personNrPasient = fnrPasient,
                tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                personNrLege = sykmelder.fnr,
                navLogId = sykmeldingId,
                msgId = sykmeldingId,
                legekontorOrgNr = null,
                legekontorOrgName = "",
                legekontorHerId = null,
                legekontorReshId = null,
                mottattDato = datoOpprettet,
                rulesetVersion = healthInformation.regelSettVersjon,
                fellesformat = objectMapper.writeValueAsString(fellesformat),
                tssid = null,
                merknader = null,
                legeHelsepersonellkategori = "LE",
                legeHprNr = hprNummer,
                partnerreferanse = null,
                vedlegg = emptyList()
            )

            receivedSykmelding.personNrPasient shouldBeEqualTo fnrPasient
            receivedSykmelding.personNrLege shouldBeEqualTo fnrLege
            receivedSykmelding.navLogId shouldBeEqualTo sykmeldingId
            receivedSykmelding.msgId shouldBeEqualTo sykmeldingId
            receivedSykmelding.legekontorOrgName shouldBeEqualTo ""
            receivedSykmelding.mottattDato shouldBeEqualTo datoOpprettet
            receivedSykmelding.tssid shouldBeEqualTo null
            receivedSykmelding.sykmelding.pasientAktoerId shouldBeEqualTo aktorId
            receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose shouldBeEqualTo Diagnose(Diagnosekoder.ICD10_CODE, "S525", "Brudd i distal ende av radius")
            receivedSykmelding.sykmelding.medisinskVurdering.biDiagnoser shouldBeEqualTo emptyList()
            receivedSykmelding.sykmelding.medisinskVurdering.svangerskap shouldBeEqualTo false
            receivedSykmelding.sykmelding.medisinskVurdering.yrkesskade shouldBeEqualTo false
            receivedSykmelding.sykmelding.medisinskVurdering.yrkesskadeDato shouldBeEqualTo null
            receivedSykmelding.sykmelding.medisinskVurdering.annenFraversArsak shouldBeEqualTo null
            receivedSykmelding.sykmelding.skjermesForPasient shouldBeEqualTo false
            receivedSykmelding.sykmelding.arbeidsgiver shouldBeEqualTo Arbeidsgiver(HarArbeidsgiver.INGEN_ARBEIDSGIVER, null, null, null)
            receivedSykmelding.sykmelding.perioder.size shouldBeEqualTo 1
            receivedSykmelding.sykmelding.perioder[0].aktivitetIkkeMulig shouldBeEqualTo AktivitetIkkeMulig(null, null)
            receivedSykmelding.sykmelding.perioder[0].fom shouldBeEqualTo LocalDate.of(2019, Month.AUGUST, 15)
            receivedSykmelding.sykmelding.perioder[0].tom shouldBeEqualTo LocalDate.of(2019, Month.SEPTEMBER, 30)
            receivedSykmelding.sykmelding.prognose shouldBeEqualTo null
            receivedSykmelding.sykmelding.utdypendeOpplysninger shouldBeEqualTo emptyMap()
            receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldBeEqualTo null
            receivedSykmelding.sykmelding.tiltakNAV shouldBeEqualTo null
            receivedSykmelding.sykmelding.andreTiltak shouldBeEqualTo null
            receivedSykmelding.sykmelding.meldingTilNAV shouldBeEqualTo null
            receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldBeEqualTo null
            receivedSykmelding.sykmelding.kontaktMedPasient shouldBeEqualTo KontaktMedPasient(null, null)
            receivedSykmelding.sykmelding.behandletTidspunkt shouldBeEqualTo LocalDateTime.of(LocalDate.of(2019, Month.AUGUST, 15), LocalTime.NOON)
            receivedSykmelding.sykmelding.behandler shouldBeEqualTo Behandler(
                fornavn = "", mellomnavn = null, etternavn = "", aktoerId = aktorIdLege, fnr = fnrLege, hpr = hprNummer, her = null, adresse = Adresse(null, null, null, null, null), tlf = null
            )
            receivedSykmelding.sykmelding.avsenderSystem shouldBeEqualTo AvsenderSystem("Papirsykmelding", journalpostId)
            receivedSykmelding.sykmelding.syketilfelleStartDato shouldBeEqualTo LocalDate.of(2019, Month.AUGUST, 15)
            receivedSykmelding.sykmelding.signaturDato shouldBeEqualTo LocalDateTime.of(LocalDate.of(2019, Month.AUGUST, 15), LocalTime.NOON)
            receivedSykmelding.sykmelding.navnFastlege shouldBeEqualTo null
        }

        test("Tilbakedatering settes riktig") {
            val skanningMetadata = skanningMetadataUnmarshaller
                .unmarshal(StringReader(getFileAsString("src/test/resources/ocr-sykmelding-komplett.xml")))
                as Skanningmetadata
            val sykmelder = Sykmelder(
                hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = "Fornavn",
                mellomnavn = null, etternavn = "Etternavn", telefonnummer = null, godkjenninger = listOf()
            )
            val pdlPerson = PdlPerson(Navn("fornavn", "mellomnavn", "etternavn"), "12345678910", "aktorid", null)

            val fellesformat = mapOcrFilTilFellesformat(
                skanningmetadata = skanningMetadata,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                loggingMeta = loggingMetadata,
                pdlPerson = pdlPerson,
                journalpostId = journalpostId
            )

            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val msgHead = fellesformat.get<XMLMsgHead>()

            val sykmelding = healthInformation.toSykmelding(
                sykmeldingId = UUID.randomUUID().toString(),
                pasientAktoerId = aktorId,
                legeAktoerId = sykmelder.aktorId,
                msgId = sykmeldingId,
                signaturDato = msgHead.msgInfo.genDate
            )

            sykmelding.kontaktMedPasient shouldBeEqualTo KontaktMedPasient(LocalDate.of(2000, 8, 10), "Han var syk i 2000 også.")
            sykmelding.behandletTidspunkt shouldBeEqualTo LocalDateTime.of(LocalDate.of(2020, Month.MAY, 2), LocalTime.NOON)
        }

        test("Map with avventendeSykmelding uten innspillTilArbeidsgiver") {
            val skanningMetadata = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/sykmelding-avventendesykmelding-ugyldig.xml"))) as Skanningmetadata
            val sykmelder = Sykmelder(hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = null, mellomnavn = null, etternavn = null, telefonnummer = null, listOf())
            val pdlPerson = PdlPerson(Navn("fornavn", "mellomnavn", "etternavn"), "12345678910", "aktorid", null)

            val func = {
                mapOcrFilTilFellesformat(
                    skanningmetadata = skanningMetadata,
                    sykmelder = sykmelder,
                    sykmeldingId = sykmeldingId,
                    loggingMeta = loggingMetadata,
                    pdlPerson = pdlPerson,
                    journalpostId = journalpostId
                )
            }

            func shouldThrow IllegalStateException::class
        }

        test("map with avventendeSykmelding og innspillTilArbeidsgiver") {
            val skanningMetadata = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/sykmelding-avventendesykmelding-gyldig.xml"))) as Skanningmetadata
            val sykmelder = Sykmelder(hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = null, mellomnavn = null, etternavn = null, telefonnummer = null, listOf())
            val pdlPerson = PdlPerson(Navn("fornavn", "mellomnavn", "etternavn"), "12345678910", "aktorid", null)

            val func = {
                mapOcrFilTilFellesformat(
                    skanningmetadata = skanningMetadata,
                    sykmelder = sykmelder,
                    sykmeldingId = sykmeldingId,
                    loggingMeta = loggingMetadata,
                    pdlPerson = pdlPerson,
                    journalpostId = journalpostId
                )
            }

            func shouldNotThrow Exception::class
        }

        test("Bruker fnr fra PDL hvis det er ulikt fnr fra ocr") {
            val fnrPasientPdl = "10987654321"
            val skanningMetadata = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/minimal-ocr-sykmelding.xml"))) as Skanningmetadata
            val sykmelder = Sykmelder(hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = null, mellomnavn = null, etternavn = null, telefonnummer = null, godkjenninger = listOf())
            val pdlPerson = PdlPerson(Navn("fornavn", "mellomnavn", "etternavn"), fnrPasientPdl, "aktorid", null)

            val fellesformat = mapOcrFilTilFellesformat(
                skanningmetadata = skanningMetadata,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                loggingMeta = loggingMetadata,
                pdlPerson = pdlPerson,
                journalpostId = journalpostId
            )

            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            healthInformation.pasient.fodselsnummer.id shouldBeEqualTo fnrPasientPdl
        }
    }

    context("Hver del av MappingService mapper korrekt") {
        test("tilMedisinskVurdering") {
            val dato = LocalDate.now()
            val medisinskVurderingType = MedisinskVurderingType().apply {
                hovedDiagnose.add(
                    HovedDiagnoseType().apply {
                        diagnosekode = "S52.5"
                        diagnose = "Syk"
                    }
                )
                bidiagnose.add(
                    BidiagnoseType().apply {
                        diagnosekode = "S69.7"
                        diagnose = "Sår hals"
                    }
                )
                isSvangerskap = false
                isYrkesskade = true
                yrkesskadedato = dato
                annenFraversArsak = "Kan ikke jobbe"
            }

            val medisinskVurdering = tilMedisinskVurdering(medisinskVurderingType, loggingMetadata)

            medisinskVurdering.hovedDiagnose?.diagnosekode?.v shouldBeEqualTo "S525"
            medisinskVurdering.hovedDiagnose?.diagnosekode?.s shouldBeEqualTo Diagnosekoder.ICD10_CODE
            medisinskVurdering.biDiagnoser.diagnosekode.size shouldBeEqualTo 1
            medisinskVurdering.biDiagnoser.diagnosekode[0].v shouldBeEqualTo "S697"
            medisinskVurdering.biDiagnoser.diagnosekode[0].s shouldBeEqualTo Diagnosekoder.ICD10_CODE
            medisinskVurdering.biDiagnoser.diagnosekode[0].dn shouldBeEqualTo "Flere skader i håndledd og hånd"
            medisinskVurdering.isSvangerskap shouldBeEqualTo false
            medisinskVurdering.isYrkesskade shouldBeEqualTo true
            medisinskVurdering.yrkesskadeDato shouldBeEqualTo dato
            medisinskVurdering.annenFraversArsak?.beskriv shouldBeEqualTo "Kan ikke jobbe"
            medisinskVurdering.annenFraversArsak?.arsakskode?.size shouldBeEqualTo 1
        }

        test("diagnoseFraDiagnosekode ICD10") {
            val diagnose = toMedisinskVurderingDiagnose(originalDiagnosekode = "S52.5", originalSystem = "ICD-10", diagnose = "foo Bar", loggingMeta = loggingMetadata)

            diagnose.s shouldBeEqualTo Diagnosekoder.ICD10_CODE
            diagnose.v shouldBeEqualTo "S525"
        }

        test("diagnoseFraDiagnosekode ICPC2") {
            val diagnose = toMedisinskVurderingDiagnose(originalDiagnosekode = "L72", originalSystem = "ICPC2", diagnose = "foo Bar", loggingMeta = loggingMetadata)

            diagnose.s shouldBeEqualTo Diagnosekoder.ICPC2_CODE
            diagnose.v shouldBeEqualTo "L72"
        }

        test("tilMedisinskVurderingDiagnose bruker ICPC2 hvis system ikke er satt men koden finnes der og ikke i ICD10") {
            val diagnose = toMedisinskVurderingDiagnose(originalDiagnosekode = "L72", originalSystem = null, diagnose = "foo Bar", loggingMeta = loggingMetadata)

            diagnose.s shouldBeEqualTo Diagnosekoder.ICPC2_CODE
            diagnose.v shouldBeEqualTo "L72"
        }

        test("tilMedisinskVurderingDiagnose bruker ICD10 hvis system ikke er satt men koden finnes der og ikke i ICPC2") {
            val diagnose = toMedisinskVurderingDiagnose(originalDiagnosekode = "S52.5", originalSystem = null, diagnose = "foo Bar", loggingMeta = loggingMetadata)

            diagnose.s shouldBeEqualTo Diagnosekoder.ICD10_CODE
            diagnose.v shouldBeEqualTo "S525"
        }

        test("tilMedisinskVurderingDiagnose feiler hvis system er ICD10 men koden finnes ikke der") {
            val func = { toMedisinskVurderingDiagnose(originalDiagnosekode = "L72", originalSystem = "ICD-10", diagnose = "foo Bar", loggingMeta = loggingMetadata) }
            func shouldThrow IllegalStateException::class
        }

        test("tilMedisinskVurderingDiagnose feiler hvis system er ICPC2 men koden finnes ikke der") {
            val func = { toMedisinskVurderingDiagnose(originalDiagnosekode = "S52.5", originalSystem = "ICPC2", diagnose = "foo Bar", loggingMeta = loggingMetadata) }
            func shouldThrow IllegalStateException::class
        }
        test("tilMedisinskVurderingDiagnose feiler hvis system er ICD10 men koden finnes kun i ICPC2") {
            val func = { toMedisinskVurderingDiagnose(originalDiagnosekode = "L60", originalSystem = "ICD-10", diagnose = "foo Bar", loggingMeta = loggingMetadata) }
            func shouldThrow IllegalStateException::class
        }

        test("diagnoseFraDiagnosekode ICD10, ugyldig kode") {
            val system = identifiserDiagnoseKodeverk(diagnoseKode = "foobar", system = "ICD-10", diagnose = "foo bar")
            system shouldBeEqualTo Diagnosekoder.ICD10_CODE
        }

        test("diagnoseFraDiagnosekode ICD10, gyldig kode") {
            val system = identifiserDiagnoseKodeverk(diagnoseKode = "S52.5", system = "ICD-10", diagnose = "foo bar")
            system shouldBeEqualTo Diagnosekoder.ICD10_CODE
        }

        test("diagnoseFraDiagnosekode ICD10, gyldig kode, gyldig diagnose, ugyldig system") {
            val system = identifiserDiagnoseKodeverk(diagnoseKode = "S52.5", system = "foo bar", diagnose = "Brudd i distal ende av radius")
            system shouldBeEqualTo Diagnosekoder.ICD10_CODE
        }

        test("diagnoseFraDiagnosekode ICPC-2, gyldig kode") {
            val system = identifiserDiagnoseKodeverk(diagnoseKode = "L72", system = "ICPC-2", diagnose = "foo bar")
            system shouldBeEqualTo Diagnosekoder.ICPC2_CODE
        }

        test("diagnoseFraDiagnosekode ICPC-2, gyldig kode, gyldig diagnose, manglende kodeverk") {
            val system = identifiserDiagnoseKodeverk(diagnoseKode = "L72", system = "", diagnose = "Brudd underarm")
            system shouldBeEqualTo Diagnosekoder.ICPC2_CODE
        }

        test("Skal ikke endre system hvis det er satt korrekt, ICPC-2") {
            val system = identifiserDiagnoseKodeverk(diagnoseKode = "foobar", system = "ICPC-2", diagnose = "foo bar")
            system shouldBeEqualTo Diagnosekoder.ICPC2_CODE
        }

        test("Skal ikke endre system hvis det er satt korrekt, ICD-10") {
            val system = identifiserDiagnoseKodeverk(diagnoseKode = "L60", system = "ICD-10", diagnose = "Inngrodd tånegl , bilateralt")
            system shouldBeEqualTo Diagnosekoder.ICD10_CODE
        }

        test("tilArbeidsgiver en arbeidsgiver") {
            val arbeidsgiverType = ArbeidsgiverType().apply {
                harArbeidsgiver = "En arbeidsgiver"
                navnArbeidsgiver = "Arbeidsgiver"
                yrkesbetegnelse = "Lærer"
                stillingsprosent = BigInteger("80")
            }

            val arbeidsgiver = tilArbeidsgiver(arbeidsgiverType, loggingMetadata)

            arbeidsgiver.harArbeidsgiver.v shouldBeEqualTo "1"
            arbeidsgiver.navnArbeidsgiver shouldBeEqualTo "Arbeidsgiver"
            arbeidsgiver.yrkesbetegnelse shouldBeEqualTo "Lærer"
            arbeidsgiver.stillingsprosent shouldBeEqualTo 80
        }

        test("tilArbeidsgiver flere arbeidsgivere") {
            val arbeidsgiverType = ArbeidsgiverType().apply {
                harArbeidsgiver = "Flere arbeidsgivere"
                navnArbeidsgiver = "Arbeidsgiver"
                yrkesbetegnelse = "Lærer"
            }

            val arbeidsgiver = tilArbeidsgiver(arbeidsgiverType, loggingMetadata)

            arbeidsgiver.harArbeidsgiver.v shouldBeEqualTo "2"
            arbeidsgiver.navnArbeidsgiver shouldBeEqualTo "Arbeidsgiver"
            arbeidsgiver.yrkesbetegnelse shouldBeEqualTo "Lærer"
        }

        test("tilArbeidsgiver ingen arbeidsgiver") {
            val arbeidsgiverType = ArbeidsgiverType().apply {
                harArbeidsgiver = "Ingen arbeidsgiver"
            }

            val arbeidsgiver = tilArbeidsgiver(arbeidsgiverType, loggingMetadata)

            arbeidsgiver.harArbeidsgiver.v shouldBeEqualTo "3"
        }

        test("tilPeriodeListe shoulld throw exception, when missing aktivitetstype") {
            val aktivitetType = AktivitetType()

            val func = { tilPeriodeListe(aktivitetType) }
            func shouldThrow IllegalStateException::class
        }

        test("tilPeriodeListe") {
            val fom = LocalDate.now()
            val tom = LocalDate.now().plusWeeks(3)
            val aktivitetType = AktivitetType().apply {
                innspillTilArbeidsgiver = "Innspill"
                avventendeSykmelding = AvventendeSykmeldingType().apply {
                    periodeFOMDato = fom
                    periodeTOMDato = tom
                }
                gradertSykmelding = GradertSykmeldingType().apply {
                    periodeFOMDato = fom
                    periodeTOMDato = tom
                    isReisetilskudd = false
                    sykmeldingsgrad = "060"
                }
                aktivitetIkkeMulig = AktivitetIkkeMuligType().apply {
                    periodeFOMDato = fom
                    periodeTOMDato = tom
                    medisinskeArsaker = MedisinskeArsakerType().apply {
                        medArsakerBesk = "syk"
                    }
                    arbeidsplassen = ArbeidsplassenType().apply {
                        arbeidsplassenBesk = "miljø"
                    }
                }
                behandlingsdager = BehandlingsdagerType().apply {
                    periodeFOMDato = fom
                    periodeTOMDato = tom
                    antallBehandlingsdager = BigInteger("2")
                }
                reisetilskudd = ReisetilskuddType().apply {
                    periodeFOMDato = fom
                    periodeTOMDato = tom
                }
            }

            val periodeliste = tilPeriodeListe(aktivitetType)

            periodeliste.size shouldBeEqualTo 5
            periodeliste[0].periodeFOMDato shouldBeEqualTo fom
            periodeliste[0].periodeTOMDato shouldBeEqualTo tom
            periodeliste[0].aktivitetIkkeMulig.medisinskeArsaker.beskriv shouldBeEqualTo "syk"
            periodeliste[0].aktivitetIkkeMulig.medisinskeArsaker.arsakskode.size shouldBeEqualTo 1
            periodeliste[0].aktivitetIkkeMulig.arbeidsplassen.beskriv shouldBeEqualTo "miljø"
            periodeliste[0].aktivitetIkkeMulig.arbeidsplassen.arsakskode.size shouldBeEqualTo 1
            periodeliste[0].avventendeSykmelding shouldBeEqualTo null
            periodeliste[0].gradertSykmelding shouldBeEqualTo null
            periodeliste[0].behandlingsdager shouldBeEqualTo null
            periodeliste[0].isReisetilskudd shouldBeEqualTo false

            periodeliste[1].periodeFOMDato shouldBeEqualTo fom
            periodeliste[1].periodeTOMDato shouldBeEqualTo tom
            periodeliste[1].aktivitetIkkeMulig shouldBeEqualTo null
            periodeliste[1].avventendeSykmelding shouldBeEqualTo null
            periodeliste[1].gradertSykmelding.sykmeldingsgrad shouldBeEqualTo 60
            periodeliste[1].behandlingsdager shouldBeEqualTo null
            periodeliste[1].isReisetilskudd shouldBeEqualTo false

            periodeliste[2].periodeFOMDato shouldBeEqualTo fom
            periodeliste[2].periodeTOMDato shouldBeEqualTo tom
            periodeliste[2].aktivitetIkkeMulig shouldBeEqualTo null
            periodeliste[2].avventendeSykmelding.innspillTilArbeidsgiver shouldBeEqualTo "Innspill"
            periodeliste[2].gradertSykmelding shouldBeEqualTo null
            periodeliste[2].behandlingsdager shouldBeEqualTo null
            periodeliste[2].isReisetilskudd shouldBeEqualTo false

            periodeliste[3].periodeFOMDato shouldBeEqualTo fom
            periodeliste[3].periodeTOMDato shouldBeEqualTo tom
            periodeliste[3].aktivitetIkkeMulig shouldBeEqualTo null
            periodeliste[3].avventendeSykmelding shouldBeEqualTo null
            periodeliste[3].gradertSykmelding shouldBeEqualTo null
            periodeliste[3].behandlingsdager.antallBehandlingsdagerUke shouldBeEqualTo 2
            periodeliste[3].isReisetilskudd shouldBeEqualTo false

            periodeliste[4].periodeFOMDato shouldBeEqualTo fom
            periodeliste[4].periodeTOMDato shouldBeEqualTo tom
            periodeliste[4].aktivitetIkkeMulig shouldBeEqualTo null
            periodeliste[4].avventendeSykmelding shouldBeEqualTo null
            periodeliste[4].gradertSykmelding shouldBeEqualTo null
            periodeliste[4].behandlingsdager shouldBeEqualTo null
            periodeliste[4].isReisetilskudd shouldBeEqualTo true
        }

        test("tilPrognose i arbeid") {
            val tilbakeIArbeidDato = LocalDate.now().plusWeeks(2)
            val datoForNyTilbakemelding = LocalDate.now().plusWeeks(1)
            val prognoseType = PrognoseType().apply {
                friskmelding = FriskmeldingType().apply {
                    isArbeidsforEtterEndtPeriode = false
                    beskrivHensynArbeidsplassen = "Hensyn"
                }
                medArbeidsgiver = MedArbeidsgiverType().apply {
                    isTilbakeAnnenArbeidsgiver = false
                    isTilbakeSammeArbeidsgiver = true
                    tilbakeDato = tilbakeIArbeidDato
                    datoNyTilbakemelding = datoForNyTilbakemelding
                }
            }

            val prognose = tilPrognose(prognoseType)

            prognose.isArbeidsforEtterEndtPeriode shouldBeEqualTo false
            prognose.beskrivHensynArbeidsplassen shouldBeEqualTo "Hensyn"
            prognose.erIArbeid?.isEgetArbeidPaSikt shouldBeEqualTo true
            prognose.erIArbeid?.isAnnetArbeidPaSikt shouldBeEqualTo false
            prognose.erIArbeid?.arbeidFraDato shouldBeEqualTo tilbakeIArbeidDato
            prognose.erIArbeid?.vurderingDato shouldBeEqualTo datoForNyTilbakemelding
            prognose.erIkkeIArbeid shouldBeEqualTo null
        }

        test("tilPrognose ikke i arbeid") {
            val tilbakeIArbeidDato = LocalDate.now().plusWeeks(2)
            val datoForNyTilbakemelding = LocalDate.now().plusWeeks(1)
            val prognoseType = PrognoseType().apply {
                utenArbeidsgiver = UtenArbeidsgiverType().apply {
                    isTilbakeArbeid = true
                    tilbakeDato = tilbakeIArbeidDato
                    datoNyTilbakemelding = datoForNyTilbakemelding
                }
            }

            val prognose = tilPrognose(prognoseType)

            prognose.isArbeidsforEtterEndtPeriode shouldBeEqualTo false
            prognose.beskrivHensynArbeidsplassen shouldBeEqualTo null
            prognose.erIkkeIArbeid?.isArbeidsforPaSikt shouldBeEqualTo true
            prognose.erIkkeIArbeid?.arbeidsforFraDato shouldBeEqualTo tilbakeIArbeidDato
            prognose.erIkkeIArbeid?.vurderingDato shouldBeEqualTo datoForNyTilbakemelding
            prognose.erIArbeid shouldBeEqualTo null
        }

        test("tilUtdypendeOpplysninger") {
            val utdypendeOpplysningerType = UtdypendeOpplysningerType().apply {
                sykehistorie = "Er syk"
                arbeidsevne = "Ikke så bra"
                behandlingsresultat = "Krysser fingrene"
                planlagtBehandling = "Legebesøk"
            }

            val utdypendeOpplysninger = tilUtdypendeOpplysninger(utdypendeOpplysningerType)

            utdypendeOpplysninger.spmGruppe.size shouldBeEqualTo 1
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.size shouldBeEqualTo 4
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.1" }?.spmId shouldBeEqualTo "6.2.1"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.1" }?.spmTekst shouldBeEqualTo "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon."
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.1" }?.restriksjon?.restriksjonskode?.firstOrNull()?.v shouldBeEqualTo "A"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.1" }?.restriksjon?.restriksjonskode?.firstOrNull()?.dn shouldBeEqualTo "Informasjonen skal ikke vises arbeidsgiver"

            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.2" }?.spmId shouldBeEqualTo "6.2.2"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.2" }?.spmTekst shouldBeEqualTo "Hvordan påvirker sykdommen arbeidsevnen?"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.2" }?.restriksjon?.restriksjonskode?.firstOrNull()?.v shouldBeEqualTo "A"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.2" }?.restriksjon?.restriksjonskode?.firstOrNull()?.dn shouldBeEqualTo "Informasjonen skal ikke vises arbeidsgiver"

            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.3" }?.spmId shouldBeEqualTo "6.2.3"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.3" }?.spmTekst shouldBeEqualTo "Har behandlingen frem til nå bedret arbeidsevnen?"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.3" }?.restriksjon?.restriksjonskode?.firstOrNull()?.v shouldBeEqualTo "A"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.3" }?.restriksjon?.restriksjonskode?.firstOrNull()?.dn shouldBeEqualTo "Informasjonen skal ikke vises arbeidsgiver"

            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.4" }?.spmId shouldBeEqualTo "6.2.4"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.4" }?.spmTekst shouldBeEqualTo "Beskriv pågående og planlagt henvisning,utredning og/eller behandling."
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.4" }?.restriksjon?.restriksjonskode?.firstOrNull()?.v shouldBeEqualTo "A"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.4" }?.restriksjon?.restriksjonskode?.firstOrNull()?.dn shouldBeEqualTo "Informasjonen skal ikke vises arbeidsgiver"
        }

        test("tilBehandler") {
            val sykmelder = Sykmelder(hprNummer = "654321", fnr = fnrLege, aktorId = aktorIdLege, fornavn = "Fornavn", mellomnavn = "Mellomnavn", etternavn = "Etternavn", telefonnummer = "12345678", godkjenninger = listOf())

            val behandler = tilBehandler(sykmelder)

            behandler.navn.fornavn shouldBeEqualTo "Fornavn"
            behandler.navn.mellomnavn shouldBeEqualTo "Mellomnavn"
            behandler.navn.etternavn shouldBeEqualTo "Etternavn"
            behandler.id.find { it.typeId.v == "FNR" }?.id shouldBeEqualTo fnrLege
            behandler.id.find { it.typeId.v == "HPR" }?.id shouldBeEqualTo "654321"
            behandler.id.find { it.typeId.v == "HER" }?.id shouldBeEqualTo null
            behandler.adresse shouldNotBeEqualTo null
            behandler.kontaktInfo.firstOrNull()?.typeTelecom?.dn shouldBeEqualTo "Hovedtelefon"
            behandler.kontaktInfo.firstOrNull()?.teleAddress?.v shouldBeEqualTo "tel:12345678"
        }

        test("velgRiktigKontaktOgSignaturDato") {
            val fom = LocalDate.of(2019, Month.SEPTEMBER, 1)
            val periodeliste = listOf(
                HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                    periodeFOMDato = fom
                    periodeTOMDato = LocalDate.of(2019, Month.OCTOBER, 16)
                    aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()
                }
            )

            val dato = velgRiktigKontaktOgSignaturDato(null, periodeliste)

            dato shouldBeEqualTo LocalDateTime.of(fom, LocalTime.NOON)
        }

        test("diagnosekodemapping små bokstaver") {
            val gyldigdiagnose = "t81.9"
            val loggingmetea = LoggingMeta(
                sykmeldingId = "1313",
                journalpostId = "5",
                hendelsesId = "2"
            )

            val hoveddiagnose = toMedisinskVurderingDiagnose(gyldigdiagnose, null, "foo Bar", loggingmetea)

            hoveddiagnose.v shouldBeEqualTo "T819"
        }

        test("Ugyldig diagnosesystem-mapping") {
            val gyldigdiagnose = "t8221.9"
            val loggingmetea = LoggingMeta(
                sykmeldingId = "1313",
                journalpostId = "5",
                hendelsesId = "2"
            )

            val func = { toMedisinskVurderingDiagnose(gyldigdiagnose, "IC", "foo Bar", loggingmetea) }
            func shouldThrow IllegalStateException::class
        }

        test("Gyldig diagnosekode mapping") {
            val gyldigdiagnose = "T81.9"
            val loggingmetea = LoggingMeta(
                sykmeldingId = "1313",
                journalpostId = "5",
                hendelsesId = "2"
            )

            val hoveddiagnose = toMedisinskVurderingDiagnose(gyldigdiagnose, null, "foo Bar", loggingmetea)

            hoveddiagnose.v shouldBeEqualTo "T819"
        }

        test("Gyldig diagnosekode mapping med space") {
            val gyldigdiagnose = "t 81.9"
            val loggingmetea = LoggingMeta(
                sykmeldingId = "1313",
                journalpostId = "5",
                hendelsesId = "2"
            )

            val hoveddiagnose = toMedisinskVurderingDiagnose(gyldigdiagnose, null, "foo Bar", loggingmetea)

            hoveddiagnose.v shouldBeEqualTo "T819"
        }
    }
})
