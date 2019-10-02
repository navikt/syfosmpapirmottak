package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
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
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.getFileAsString
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.ArbeidsrelatertArsak
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.Periode
import no.nav.syfo.model.SporsmalSvar
import no.nav.syfo.model.SvarRestriksjon
import no.nav.syfo.skanningMetadataUnmarshaller
import no.nav.syfo.sm.Diagnosekoder
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month

@KtorExperimentalAPI
object MappingServiceSpek : Spek ({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val fnrPasient = "12345678910"
    val aktorId = "aktorId"
    val fnrLege = "fnrLege"
    val aktorIdLege = "aktorIdLege"
    val hprNummer = "10052512"
    val datoOpprettet = LocalDateTime.now()
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

    val mappingService = MappingService()

    describe("MappingService ende-til-ende") {
        it("Realistisk case ende-til-ende") {
            val skanningMetadata = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/ocr-sykmelding.xml"))) as Skanningmetadata
            val sykmelder = Sykmelder(hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = "Fornavn", mellomnavn = null, etternavn = "Etternavn")

            val receivedSykmelding = mappingService.mapOcrFilTilReceivedSykmelding(
                skanningmetadata = skanningMetadata,
                fnr = fnrPasient,
                aktorId = aktorId,
                datoOpprettet = datoOpprettet,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                loggingMeta = loggingMetadata)

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

            val receivedSykmelding = mappingService.mapOcrFilTilReceivedSykmelding(
                skanningmetadata = skanningMetadata,
                fnr = fnrPasient,
                aktorId = aktorId,
                datoOpprettet = datoOpprettet,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                loggingMeta = loggingMetadata)

            receivedSykmelding.personNrPasient shouldEqual fnrPasient
            receivedSykmelding.personNrLege shouldEqual fnrLege
            receivedSykmelding.navLogId shouldEqual sykmeldingId
            receivedSykmelding.msgId shouldEqual sykmeldingId
            receivedSykmelding.legekontorOrgName shouldEqual ""
            receivedSykmelding.mottattDato shouldEqual datoOpprettet
            receivedSykmelding.tssid shouldEqual null
            receivedSykmelding.sykmelding.pasientAktoerId shouldEqual aktorId
            receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose shouldEqual Diagnose(Diagnosekoder.ICD10_CODE, "S52.5")
            receivedSykmelding.sykmelding.medisinskVurdering.biDiagnoser shouldEqual emptyList()
            receivedSykmelding.sykmelding.medisinskVurdering.svangerskap shouldEqual false
            receivedSykmelding.sykmelding.medisinskVurdering.yrkesskade shouldEqual false
            receivedSykmelding.sykmelding.medisinskVurdering.yrkesskadeDato shouldEqual null
            receivedSykmelding.sykmelding.medisinskVurdering.annenFraversArsak shouldEqual null
            receivedSykmelding.sykmelding.skjermesForPasient shouldEqual false
            receivedSykmelding.sykmelding.arbeidsgiver shouldEqual Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, null, null, null)
            receivedSykmelding.sykmelding.perioder.size shouldEqual 1
            receivedSykmelding.sykmelding.perioder[0].aktivitetIkkeMulig shouldEqual AktivitetIkkeMulig(MedisinskArsak(null, emptyList()), null)
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
            receivedSykmelding.sykmelding.syketilfelleStartDato shouldEqual LocalDate.of(2019, Month.AUGUST, 15)
            receivedSykmelding.sykmelding.signaturDato shouldEqual LocalDateTime.of(LocalDate.of(2019, Month.AUGUST, 15), LocalTime.NOON)
            receivedSykmelding.sykmelding.navnFastlege shouldEqual null
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

            val medisinskVurdering = mappingService.tilMedisinskVurdering(medisinskVurderingType, loggingMetadata)

            medisinskVurdering.hovedDiagnose?.kode shouldEqual "S52.5"
            medisinskVurdering.hovedDiagnose?.system shouldEqual Diagnosekoder.ICD10_CODE
            medisinskVurdering.biDiagnoser.size shouldEqual 1
            medisinskVurdering.biDiagnoser[0] shouldEqual Diagnose(system = Diagnosekoder.ICD10_CODE, kode = "S69.7")
            medisinskVurdering.svangerskap shouldEqual false
            medisinskVurdering.yrkesskade shouldEqual true
            medisinskVurdering.yrkesskadeDato shouldEqual dato
            medisinskVurdering.annenFraversArsak?.beskrivelse shouldEqual "Kan ikke jobbe"
            medisinskVurdering.annenFraversArsak?.grunn?.size shouldEqual 0
        }

        it("diagnosekodeSystemFraDiagnosekode ICD10") {
            val diagnosekodeSystem = mappingService.diagnosekodeSystemFraDiagnosekode("S52.5", loggingMetadata)

            diagnosekodeSystem shouldEqual Diagnosekoder.ICD10_CODE
        }

        it("diagnosekodeSystemFraDiagnosekode ICPC2") {
            val diagnosekodeSystem = mappingService.diagnosekodeSystemFraDiagnosekode("L72", loggingMetadata)

            diagnosekodeSystem shouldEqual Diagnosekoder.ICPC2_CODE
        }

        it("tilArbeidsgiver en arbeidsgiver") {
            val arbeidsgiverType = ArbeidsgiverType().apply {
                harArbeidsgiver = "En arbeidsgiver"
                navnArbeidsgiver = "Arbeidsgiver"
                yrkesbetegnelse = "Lærer"
                stillingsprosent = BigInteger("80")
            }

            val arbeidsgiver = mappingService.tilArbeidsgiver(arbeidsgiverType, loggingMetadata)

            arbeidsgiver.harArbeidsgiver shouldEqual HarArbeidsgiver.EN_ARBEIDSGIVER
            arbeidsgiver.navn shouldEqual "Arbeidsgiver"
            arbeidsgiver.yrkesbetegnelse shouldEqual "Lærer"
            arbeidsgiver.stillingsprosent shouldEqual 80
        }

        it("tilArbeidsgiver flere arbeidsgivere") {
            val arbeidsgiverType = ArbeidsgiverType().apply {
                harArbeidsgiver = "Flere arbeidsgivere"
                navnArbeidsgiver = "Arbeidsgiver"
                yrkesbetegnelse = "Lærer"
            }

            val arbeidsgiver = mappingService.tilArbeidsgiver(arbeidsgiverType, loggingMetadata)

            arbeidsgiver.harArbeidsgiver shouldEqual HarArbeidsgiver.FLERE_ARBEIDSGIVERE
            arbeidsgiver.navn shouldEqual "Arbeidsgiver"
            arbeidsgiver.yrkesbetegnelse shouldEqual "Lærer"
        }

        it("tilArbeidsgiver ingen arbeidsgiver") {
            val arbeidsgiverType = ArbeidsgiverType().apply {
                harArbeidsgiver = "Ingen arbeidsgiver"
            }

            val arbeidsgiver = mappingService.tilArbeidsgiver(arbeidsgiverType, loggingMetadata)

            arbeidsgiver.harArbeidsgiver shouldEqual HarArbeidsgiver.INGEN_ARBEIDSGIVER
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

            val periodeliste = mappingService.tilPeriodeListe(aktivitetType)

            periodeliste.size shouldEqual 5
            periodeliste[0] shouldEqual Periode(fom, tom, AktivitetIkkeMulig(
                MedisinskArsak("syk", emptyList()), ArbeidsrelatertArsak("miljø", emptyList())),
                null, null, null, false)
            periodeliste[1] shouldEqual Periode(fom, tom, null,
                null, null, Gradert(false, 60), false)
            periodeliste[2] shouldEqual Periode(fom, tom, null,
                "Innspill", null, null, false)
            periodeliste[3] shouldEqual Periode(fom, tom, null,
                null, 2, null, false)
            periodeliste[4] shouldEqual Periode(fom, tom, null,
                null, null, null, true)
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

            val prognose = mappingService.tilPrognose(prognoseType)

            prognose.arbeidsforEtterPeriode shouldEqual false
            prognose.hensynArbeidsplassen shouldEqual "Hensyn"
            prognose.erIArbeid?.egetArbeidPaSikt shouldEqual true
            prognose.erIArbeid?.annetArbeidPaSikt shouldEqual false
            prognose.erIArbeid?.arbeidFOM shouldEqual tilbakeIArbeidDato
            prognose.erIArbeid?.vurderingsdato shouldEqual datoForNyTilbakemelding
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

            val prognose = mappingService.tilPrognose(prognoseType)

            prognose.arbeidsforEtterPeriode shouldEqual true
            prognose.hensynArbeidsplassen shouldEqual null
            prognose.erIkkeIArbeid?.arbeidsforPaSikt shouldEqual true
            prognose.erIkkeIArbeid?.arbeidsforFOM shouldEqual tilbakeIArbeidDato
            prognose.erIkkeIArbeid?.vurderingsdato shouldEqual datoForNyTilbakemelding
            prognose.erIArbeid shouldEqual null
        }

        it("tilUtdypendeOpplysninger") {
            val utdypendeOpplysningerType = UtdypendeOpplysningerType().apply {
                sykehistorie = "Er syk"
                arbeidsevne = "Ikke så bra"
                behandlingsresultat = "Krysser fingrene"
                planlagtBehandling = "Legebesøk"
            }

            val utdypendeOpplysninger = mappingService.tilUtdypendeOpplysninger(utdypendeOpplysningerType)

            utdypendeOpplysninger.size shouldEqual 1
            utdypendeOpplysninger["6.2"]?.size shouldEqual 4
            utdypendeOpplysninger["6.2"]?.get("6.2.1") shouldEqual SporsmalSvar(sporsmal = "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon.", svar = "Er syk", restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER))
            utdypendeOpplysninger["6.2"]?.get("6.2.2") shouldEqual SporsmalSvar(sporsmal = "Hvordan påvirker sykdommen arbeidsevnen?", svar = "Ikke så bra", restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER))
            utdypendeOpplysninger["6.2"]?.get("6.2.3") shouldEqual SporsmalSvar(sporsmal = "Har behandlingen frem til nå bedret arbeidsevnen?", svar = "Krysser fingrene", restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER))
            utdypendeOpplysninger["6.2"]?.get("6.2.4") shouldEqual SporsmalSvar(sporsmal = "Beskriv pågående og planlagt henvisning,utredning og/eller behandling.", svar = "Legebesøk", restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER))
        }

        it("tilBehandler") {
            val sykmelder = Sykmelder(hprNummer = "654321", fnr = fnrLege, aktorId = aktorIdLege, fornavn = "Fornavn", mellomnavn = "Mellomnavn", etternavn = "Etternavn")

            val behandler = mappingService.tilBehandler(sykmelder)

            behandler.fornavn shouldEqual "Fornavn"
            behandler.mellomnavn shouldEqual "Mellomnavn"
            behandler.etternavn shouldEqual "Etternavn"
            behandler.aktoerId shouldEqual aktorIdLege
            behandler.fnr shouldEqual fnrLege
            behandler.hpr shouldEqual "654321"
            behandler.her shouldEqual null
            behandler.adresse shouldNotEqual null
            behandler.tlf shouldEqual null
        }
    }
})
