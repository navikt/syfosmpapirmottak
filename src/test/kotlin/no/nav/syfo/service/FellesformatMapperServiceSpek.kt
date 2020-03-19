package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.DynaSvarType
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import java.io.StringReader
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
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
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.get
import no.nav.syfo.util.skanningMetadataUnmarshaller
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.amshove.kluent.shouldNotThrow
import org.amshove.kluent.shouldThrow
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper

@KtorExperimentalAPI
object FellesformatMapperServiceSpek : Spek({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val fnrPasient = "12345678910"
    val aktorId = "aktorId"
    val fnrLege = "fnrLege"
    val aktorIdLege = "aktorIdLege"
    val hprNummer = "10052512"
    val datoOpprettet = LocalDateTime.now()
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

    describe("MappingService ende-til-ende") {
        it("Realistisk case ende-til-ende") {
            val skanningMetadata = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/ocr-sykmelding.xml"))) as Skanningmetadata
            val sykmelder = Sykmelder(hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = "Fornavn", mellomnavn = null, etternavn = "Etternavn")

            val fellesformat = mapOcrFilTilFellesformat(
                    skanningmetadata = skanningMetadata,
                    fnr = fnrPasient,
                    datoOpprettet = datoOpprettet,
                    sykmelder = sykmelder,
                    sykmeldingId = sykmeldingId,
                    loggingMeta = loggingMetadata)

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
                    tssid = null
            )

            receivedSykmelding.personNrPasient shouldEqual fnrPasient
            receivedSykmelding.personNrLege shouldEqual fnrLege
            receivedSykmelding.navLogId shouldEqual sykmeldingId
            receivedSykmelding.msgId shouldEqual sykmeldingId
            receivedSykmelding.legekontorOrgName shouldEqual ""
            receivedSykmelding.mottattDato shouldEqual datoOpprettet
            receivedSykmelding.tssid shouldEqual null
            receivedSykmelding.sykmelding.pasientAktoerId shouldEqual aktorId
            receivedSykmelding.sykmelding.medisinskVurdering shouldNotEqual null
            receivedSykmelding.sykmelding.skjermesForPasient shouldEqual false
            receivedSykmelding.sykmelding.arbeidsgiver shouldNotEqual null
            receivedSykmelding.sykmelding.perioder.size shouldEqual 1
            receivedSykmelding.sykmelding.prognose shouldEqual null
            receivedSykmelding.sykmelding.utdypendeOpplysninger shouldEqual emptyMap()
            receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldEqual null
            receivedSykmelding.sykmelding.tiltakNAV shouldEqual null
            receivedSykmelding.sykmelding.andreTiltak shouldEqual null
            receivedSykmelding.sykmelding.meldingTilNAV?.bistandUmiddelbart shouldEqual true
            receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldEqual null
            receivedSykmelding.sykmelding.kontaktMedPasient shouldEqual KontaktMedPasient(null, null)
            receivedSykmelding.sykmelding.behandletTidspunkt shouldEqual LocalDateTime.of(LocalDate.of(2019, Month.AUGUST, 15), LocalTime.NOON)
            receivedSykmelding.sykmelding.behandler shouldNotEqual null
            receivedSykmelding.sykmelding.avsenderSystem shouldEqual AvsenderSystem("Papirsykmelding", "1")
            receivedSykmelding.sykmelding.syketilfelleStartDato shouldEqual LocalDate.of(2019, Month.AUGUST, 15)
            receivedSykmelding.sykmelding.signaturDato shouldEqual LocalDateTime.of(LocalDate.of(2019, Month.AUGUST, 15), LocalTime.NOON)
            receivedSykmelding.sykmelding.navnFastlege shouldEqual null
        }

        it("Minimal ocr-fil") {
            val skanningMetadata = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/minimal-ocr-sykmelding.xml"))) as Skanningmetadata
            val sykmelder = Sykmelder(hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = null, mellomnavn = null, etternavn = null)

            val fellesformat = mapOcrFilTilFellesformat(
                    skanningmetadata = skanningMetadata,
                    fnr = fnrPasient,
                    datoOpprettet = datoOpprettet,
                    sykmelder = sykmelder,
                    sykmeldingId = sykmeldingId,
                    loggingMeta = loggingMetadata)

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
                    tssid = null
            )

            receivedSykmelding.personNrPasient shouldEqual fnrPasient
            receivedSykmelding.personNrLege shouldEqual fnrLege
            receivedSykmelding.navLogId shouldEqual sykmeldingId
            receivedSykmelding.msgId shouldEqual sykmeldingId
            receivedSykmelding.legekontorOrgName shouldEqual ""
            receivedSykmelding.mottattDato shouldEqual datoOpprettet
            receivedSykmelding.tssid shouldEqual null
            receivedSykmelding.sykmelding.pasientAktoerId shouldEqual aktorId
            receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose shouldEqual Diagnose(Diagnosekoder.ICD10_CODE, "S525", "Brudd i distal ende av radius")
            receivedSykmelding.sykmelding.medisinskVurdering.biDiagnoser shouldEqual emptyList()
            receivedSykmelding.sykmelding.medisinskVurdering.svangerskap shouldEqual false
            receivedSykmelding.sykmelding.medisinskVurdering.yrkesskade shouldEqual false
            receivedSykmelding.sykmelding.medisinskVurdering.yrkesskadeDato shouldEqual null
            receivedSykmelding.sykmelding.medisinskVurdering.annenFraversArsak shouldEqual null
            receivedSykmelding.sykmelding.skjermesForPasient shouldEqual false
            receivedSykmelding.sykmelding.arbeidsgiver shouldEqual Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, null, null, null)
            receivedSykmelding.sykmelding.perioder.size shouldEqual 1
            receivedSykmelding.sykmelding.perioder[0].aktivitetIkkeMulig shouldEqual AktivitetIkkeMulig(null, null)
            receivedSykmelding.sykmelding.perioder[0].fom shouldEqual LocalDate.of(2019, Month.AUGUST, 15)
            receivedSykmelding.sykmelding.perioder[0].tom shouldEqual LocalDate.of(2019, Month.SEPTEMBER, 30)
            receivedSykmelding.sykmelding.prognose shouldEqual null
            receivedSykmelding.sykmelding.utdypendeOpplysninger shouldEqual emptyMap()
            receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldEqual null
            receivedSykmelding.sykmelding.tiltakNAV shouldEqual null
            receivedSykmelding.sykmelding.andreTiltak shouldEqual null
            receivedSykmelding.sykmelding.meldingTilNAV shouldEqual null
            receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldEqual null
            receivedSykmelding.sykmelding.kontaktMedPasient shouldEqual KontaktMedPasient(null, null)
            receivedSykmelding.sykmelding.behandletTidspunkt shouldEqual LocalDateTime.of(LocalDate.of(2019, Month.AUGUST, 15), LocalTime.NOON)
            receivedSykmelding.sykmelding.behandler shouldEqual Behandler(
                    fornavn = "", mellomnavn = null, etternavn = "", aktoerId = aktorIdLege, fnr = fnrLege, hpr = hprNummer, her = null, adresse = Adresse(null, null, null, null, null), tlf = null)
            receivedSykmelding.sykmelding.avsenderSystem shouldEqual AvsenderSystem("Papirsykmelding", "1")
            receivedSykmelding.sykmelding.syketilfelleStartDato shouldEqual null
            receivedSykmelding.sykmelding.signaturDato shouldEqual LocalDateTime.of(LocalDate.of(2019, Month.AUGUST, 15), LocalTime.NOON)
            receivedSykmelding.sykmelding.navnFastlege shouldEqual null
        }

        it("Map with avventendeSykmelding uten innspillTilArbeidsgiver") {
            val skanningMetadata = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/sykmelding-avventendesykmelding-ugyldig.xml"))) as Skanningmetadata
            val sykmelder = Sykmelder(hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = null, mellomnavn = null, etternavn = null)

            val func = {
                mapOcrFilTilFellesformat(
                        skanningmetadata = skanningMetadata,
                        fnr = fnrPasient,
                        datoOpprettet = datoOpprettet,
                        sykmelder = sykmelder,
                        sykmeldingId = sykmeldingId,
                        loggingMeta = loggingMetadata)
            }

            func shouldThrow IllegalStateException::class
        }

        it("map with avventendeSykmelding og innspillTilArbeidsgiver") {
            val skanningMetadata = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/sykmelding-avventendesykmelding-gyldig.xml"))) as Skanningmetadata
            val sykmelder = Sykmelder(hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = null, mellomnavn = null, etternavn = null)


            val func = {
                mapOcrFilTilFellesformat(
                        skanningmetadata = skanningMetadata,
                        fnr = fnrPasient,
                        datoOpprettet = datoOpprettet,
                        sykmelder = sykmelder,
                        sykmeldingId = sykmeldingId,
                        loggingMeta = loggingMetadata)
            }

            func shouldNotThrow Exception::class
        }
    }

    describe("Hver del av MappingService mapper korrekt") {
        it("tilMedisinskVurdering") {
            val dato = LocalDate.now()
            val medisinskVurderingType = MedisinskVurderingType().apply {
                hovedDiagnose.add(HovedDiagnoseType().apply {
                    diagnosekode = "S52.5"
                    diagnose = "Syk"
                })
                bidiagnose.add(BidiagnoseType().apply {
                    diagnosekode = "S69.7"
                    diagnose = "Sår hals"
                })
                isSvangerskap = false
                isYrkesskade = true
                yrkesskadedato = dato
                annenFraversArsak = "Kan ikke jobbe"
            }

            val medisinskVurdering = tilMedisinskVurdering(medisinskVurderingType)

            medisinskVurdering.hovedDiagnose?.diagnosekode?.v shouldEqual "S525"
            medisinskVurdering.hovedDiagnose?.diagnosekode?.s shouldEqual Diagnosekoder.ICD10_CODE
            medisinskVurdering.biDiagnoser.diagnosekode.size shouldEqual 1
            medisinskVurdering.biDiagnoser.diagnosekode[0] shouldEqual Diagnose(
                    system = Diagnosekoder.ICD10_CODE,
                    kode = "S697",
                    tekst = "Flere skader i håndledd og hånd")
            medisinskVurdering.isSvangerskap shouldEqual false
            medisinskVurdering.isYrkesskade shouldEqual true
            medisinskVurdering.yrkesskadeDato shouldEqual dato
            medisinskVurdering.annenFraversArsak?.beskriv shouldEqual "Kan ikke jobbe"
            medisinskVurdering.annenFraversArsak?.arsakskode?.size shouldEqual 0
        }

        it("diagnoseFraDiagnosekode ICD10") {
            val diagnose = toMedisinskVurderingDiagnode("S52.5")

            diagnose.s shouldEqual Diagnosekoder.ICD10_CODE
            diagnose.v shouldEqual "S525"
        }

        it("diagnoseFraDiagnosekode ICPC2") {
            val diagnose = toMedisinskVurderingDiagnode("L72")

            diagnose.s shouldEqual Diagnosekoder.ICPC2_CODE
            diagnose.v shouldEqual "L72"
        }

        it("tilArbeidsgiver en arbeidsgiver") {
            val arbeidsgiverType = ArbeidsgiverType().apply {
                harArbeidsgiver = "En arbeidsgiver"
                navnArbeidsgiver = "Arbeidsgiver"
                yrkesbetegnelse = "Lærer"
                stillingsprosent = BigInteger("80")
            }

            val arbeidsgiver = tilArbeidsgiver(arbeidsgiverType)

            arbeidsgiver.harArbeidsgiver.v shouldEqual "1"
            arbeidsgiver.navnArbeidsgiver shouldEqual "Arbeidsgiver"
            arbeidsgiver.yrkesbetegnelse shouldEqual "Lærer"
            arbeidsgiver.stillingsprosent shouldEqual 80
        }

        it("tilArbeidsgiver flere arbeidsgivere") {
            val arbeidsgiverType = ArbeidsgiverType().apply {
                harArbeidsgiver = "Flere arbeidsgivere"
                navnArbeidsgiver = "Arbeidsgiver"
                yrkesbetegnelse = "Lærer"
            }

            val arbeidsgiver = tilArbeidsgiver(arbeidsgiverType)

            arbeidsgiver.harArbeidsgiver.v shouldEqual "2"
            arbeidsgiver.navnArbeidsgiver shouldEqual "Arbeidsgiver"
            arbeidsgiver.yrkesbetegnelse shouldEqual "Lærer"
        }

        it("tilArbeidsgiver ingen arbeidsgiver") {
            val arbeidsgiverType = ArbeidsgiverType().apply {
                harArbeidsgiver = "Ingen arbeidsgiver"
            }

            val arbeidsgiver = tilArbeidsgiver(arbeidsgiverType)

            arbeidsgiver.harArbeidsgiver.v shouldEqual "3"
        }

        it("tilPeriodeListe shoulld throw exception, when missing aktivitetstype") {
            val fom = LocalDate.now()
            val tom = LocalDate.now().plusDays(1)

            val aktivitetType = AktivitetType()

            val func = { tilPeriodeListe(aktivitetType) }
            func shouldThrow IllegalStateException::class
        }

        it("tilPeriodeListe") {
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

            periodeliste.size shouldEqual 5
            periodeliste[0].periodeFOMDato shouldEqual fom
            periodeliste[0].periodeTOMDato shouldEqual tom
            periodeliste[0].aktivitetIkkeMulig.medisinskeArsaker.beskriv shouldEqual "syk"
            periodeliste[0].aktivitetIkkeMulig.medisinskeArsaker.arsakskode.size shouldEqual 1
            periodeliste[0].aktivitetIkkeMulig.arbeidsplassen.beskriv shouldEqual "miljø"
            periodeliste[0].aktivitetIkkeMulig.arbeidsplassen.arsakskode.size shouldEqual 1
            periodeliste[0].avventendeSykmelding shouldEqual null
            periodeliste[0].gradertSykmelding shouldEqual null
            periodeliste[0].behandlingsdager shouldEqual null
            periodeliste[0].isReisetilskudd shouldEqual false

            periodeliste[1].periodeFOMDato shouldEqual fom
            periodeliste[1].periodeTOMDato shouldEqual tom
            periodeliste[1].aktivitetIkkeMulig shouldEqual null
            periodeliste[1].avventendeSykmelding shouldEqual null
            periodeliste[1].gradertSykmelding.sykmeldingsgrad shouldEqual 60
            periodeliste[1].behandlingsdager shouldEqual null
            periodeliste[1].isReisetilskudd shouldEqual false

            periodeliste[2].periodeFOMDato shouldEqual fom
            periodeliste[2].periodeTOMDato shouldEqual tom
            periodeliste[2].aktivitetIkkeMulig shouldEqual null
            periodeliste[2].avventendeSykmelding.innspillTilArbeidsgiver shouldEqual "Innspill"
            periodeliste[2].gradertSykmelding shouldEqual null
            periodeliste[2].behandlingsdager shouldEqual null
            periodeliste[2].isReisetilskudd shouldEqual false

            periodeliste[3].periodeFOMDato shouldEqual fom
            periodeliste[3].periodeTOMDato shouldEqual tom
            periodeliste[3].aktivitetIkkeMulig shouldEqual null
            periodeliste[3].avventendeSykmelding shouldEqual null
            periodeliste[3].gradertSykmelding shouldEqual null
            periodeliste[3].behandlingsdager.antallBehandlingsdagerUke shouldEqual 2
            periodeliste[3].isReisetilskudd shouldEqual false

            periodeliste[4].periodeFOMDato shouldEqual fom
            periodeliste[4].periodeTOMDato shouldEqual tom
            periodeliste[4].aktivitetIkkeMulig shouldEqual null
            periodeliste[4].avventendeSykmelding shouldEqual null
            periodeliste[4].gradertSykmelding shouldEqual null
            periodeliste[4].behandlingsdager shouldEqual null
            periodeliste[4].isReisetilskudd shouldEqual true


            /*
            periodeliste[1] shouldEqual Periode(fom, tom, null,
                    null, null, Gradert(false, 60), false)
            periodeliste[2] shouldEqual Periode(fom, tom, null,
                    "Innspill", null, null, false)
            periodeliste[3] shouldEqual Periode(fom, tom, null,
                    null, 2, null, false)
            periodeliste[4] shouldEqual Periode(fom, tom, null,
                    null, null, null, true)

             */
        }

        it("tilPrognose i arbeid") {
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

            prognose.isArbeidsforEtterEndtPeriode shouldEqual false
            prognose.beskrivHensynArbeidsplassen shouldEqual "Hensyn"
            prognose.erIArbeid?.isEgetArbeidPaSikt shouldEqual true
            prognose.erIArbeid?.isAnnetArbeidPaSikt shouldEqual false
            prognose.erIArbeid?.arbeidFraDato shouldEqual tilbakeIArbeidDato
            prognose.erIArbeid?.vurderingDato shouldEqual datoForNyTilbakemelding
            prognose.erIkkeIArbeid shouldEqual null
        }

        it("tilPrognose ikke i arbeid") {
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

            prognose.isArbeidsforEtterEndtPeriode shouldEqual true
            prognose.beskrivHensynArbeidsplassen shouldEqual null
            prognose.erIkkeIArbeid?.isArbeidsforPaSikt shouldEqual true
            prognose.erIkkeIArbeid?.arbeidsforFraDato shouldEqual tilbakeIArbeidDato
            prognose.erIkkeIArbeid?.vurderingDato shouldEqual datoForNyTilbakemelding
            prognose.erIArbeid shouldEqual null
        }

        it("tilUtdypendeOpplysninger") {
            val utdypendeOpplysningerType = UtdypendeOpplysningerType().apply {
                sykehistorie = "Er syk"
                arbeidsevne = "Ikke så bra"
                behandlingsresultat = "Krysser fingrene"
                planlagtBehandling = "Legebesøk"
            }

            val utdypendeOpplysninger = tilUtdypendeOpplysninger(utdypendeOpplysningerType)

            utdypendeOpplysninger.spmGruppe.size shouldEqual 1
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.size shouldEqual 4
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.1" }?.spmId shouldEqual "6.2.1"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.1" }?.spmTekst shouldEqual "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon."
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.1" }?.restriksjon?.restriksjonskode?.firstOrNull()?.v shouldEqual "A"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.1" }?.restriksjon?.restriksjonskode?.firstOrNull()?.dn shouldEqual "Informasjonen skal ikke vises arbeidsgiver"

            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.2" }?.spmId shouldEqual "6.2.2"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.2" }?.spmTekst shouldEqual "Hvordan påvirker sykdommen arbeidsevnen?"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.2" }?.restriksjon?.restriksjonskode?.firstOrNull()?.v shouldEqual "A"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.2" }?.restriksjon?.restriksjonskode?.firstOrNull()?.dn shouldEqual "Informasjonen skal ikke vises arbeidsgiver"

            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.3" }?.spmId shouldEqual "6.2.3"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.3" }?.spmTekst shouldEqual "Har behandlingen frem til nå bedret arbeidsevnen?"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.3" }?.restriksjon?.restriksjonskode?.firstOrNull()?.v shouldEqual "A"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.3" }?.restriksjon?.restriksjonskode?.firstOrNull()?.dn shouldEqual "Informasjonen skal ikke vises arbeidsgiver"

            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.4" }?.spmId shouldEqual "6.2.4"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.4" }?.spmTekst shouldEqual "Beskriv pågående og planlagt henvisning,utredning og/eller behandling."
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.4" }?.restriksjon?.restriksjonskode?.firstOrNull()?.v shouldEqual "A"
            utdypendeOpplysninger.spmGruppe.first().spmSvar?.find { it.spmId == "6.2.4" }?.restriksjon?.restriksjonskode?.firstOrNull()?.dn shouldEqual "Informasjonen skal ikke vises arbeidsgiver"

        }

        it("tilBehandler") {
            val sykmelder = Sykmelder(hprNummer = "654321", fnr = fnrLege, aktorId = aktorIdLege, fornavn = "Fornavn", mellomnavn = "Mellomnavn", etternavn = "Etternavn")

            val behandler = tilBehandler(sykmelder)

            behandler.navn.fornavn shouldEqual "Fornavn"
            behandler.navn.mellomnavn shouldEqual "Mellomnavn"
            behandler.navn.etternavn shouldEqual "Etternavn"
            behandler.id.find { it.typeId.v == "FNR" }?.id shouldEqual fnrLege
            behandler.id.find { it.typeId.v == "HPR" }?.id shouldEqual "654321"
            behandler.id.find { it.typeId.v == "HER" }?.id shouldEqual null
            behandler.adresse shouldNotEqual null
            behandler.kontaktInfo.firstOrNull()?.typeTelecom?.dn shouldEqual null
        }

        it("velgRiktigKontaktOgSignaturDato") {
            val fom = LocalDate.of(2019, Month.SEPTEMBER, 1)
            val periodeliste = listOf(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = fom
                periodeTOMDato = LocalDate.of(2019, Month.OCTOBER, 16)
                aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()

            })

            val dato = velgRiktigKontaktOgSignaturDato(null, periodeliste)

            dato shouldEqual LocalDateTime.of(fom, LocalTime.NOON)
        }
    }
})
