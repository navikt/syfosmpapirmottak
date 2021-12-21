package no.nav.syfo.service

import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.client.getFileAsString
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.SvarRestriksjon
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.util.skanningMetadataUnmarshaller
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

object PapirSmMapperServiceSpek : Spek({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val oppgaveId = "123"
    val dokumentInfoId = "123"
    val fnrPasient = "12345678910"
    val aktorId = "aktorId"
    val datoOpprettet = OffsetDateTime.now(ZoneOffset.UTC)

    describe("PapirSmMappingService") {

        it("test OCR file with empty behandlngsdager") {
            val ocrFil = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/ocr-sykmelding-komplett-uten-behandlingsdager.xml"))) as Skanningmetadata

            val papirSm = mapOcrFilTilPapirSmRegistrering(
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                fnr = fnrPasient,
                aktorId = aktorId,
                dokumentInfoId = dokumentInfoId,
                datoOpprettet = datoOpprettet,
                sykmeldingId = sykmeldingId,
                sykmelder = null,
                ocrFil = ocrFil
            )

            papirSm.perioder shouldNotBe null
        }

        it("Test ocr file with empty hoveddiagnoselist") {
            val ocrFil = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/ocr-sykmelding-komplett-uten-hoveddiagnose.xml"))) as Skanningmetadata

            val papirSm = mapOcrFilTilPapirSmRegistrering(
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                fnr = fnrPasient,
                aktorId = aktorId,
                dokumentInfoId = dokumentInfoId,
                datoOpprettet = datoOpprettet,
                sykmeldingId = sykmeldingId,
                sykmelder = null,
                ocrFil = ocrFil
            )

            papirSm.medisinskVurdering?.hovedDiagnose shouldBe null
        }

        it("Tests a complete OCR file, and should parse fine") {
            val ocrFil = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/ocr-sykmelding-komplett.xml"))) as Skanningmetadata

            val papirSm = mapOcrFilTilPapirSmRegistrering(
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                fnr = fnrPasient,
                aktorId = aktorId,
                dokumentInfoId = dokumentInfoId,
                datoOpprettet = datoOpprettet,
                sykmeldingId = sykmeldingId,
                sykmelder = null,
                ocrFil = ocrFil
            )

            papirSm.journalpostId shouldBeEqualTo journalpostId
            papirSm.fnr shouldBeEqualTo fnrPasient
            papirSm.aktorId shouldBeEqualTo aktorId
            papirSm.dokumentInfoId shouldBeEqualTo dokumentInfoId
            papirSm.datoOpprettet shouldBeEqualTo datoOpprettet
            papirSm.sykmeldingId shouldBeEqualTo sykmeldingId
            papirSm.syketilfelleStartDato shouldBeEqualTo ocrFil.sykemeldinger.syketilfelleStartDato

            papirSm.arbeidsgiver?.harArbeidsgiver shouldBeEqualTo HarArbeidsgiver.EN_ARBEIDSGIVER
            papirSm.arbeidsgiver?.navn shouldBeEqualTo ocrFil.sykemeldinger.arbeidsgiver.navnArbeidsgiver
            papirSm.arbeidsgiver?.stillingsprosent shouldBeEqualTo ocrFil.sykemeldinger.arbeidsgiver.stillingsprosent.toInt()
            papirSm.arbeidsgiver?.yrkesbetegnelse shouldBeEqualTo ocrFil.sykemeldinger.arbeidsgiver.yrkesbetegnelse

            papirSm.medisinskVurdering?.hovedDiagnose?.kode shouldBeEqualTo ocrFil.sykemeldinger.medisinskVurdering.hovedDiagnose.first().diagnosekode
            papirSm.medisinskVurdering?.hovedDiagnose?.system shouldBeEqualTo Diagnosekoder.ICD10_CODE
            papirSm.medisinskVurdering?.hovedDiagnose?.tekst shouldBeEqualTo ocrFil.sykemeldinger.medisinskVurdering.hovedDiagnose.first().diagnose
            papirSm.medisinskVurdering?.biDiagnoser?.first()?.system shouldBeEqualTo Diagnosekoder.ICD10_CODE
            papirSm.medisinskVurdering?.svangerskap shouldBeEqualTo ocrFil.sykemeldinger.medisinskVurdering.isSvangerskap
            papirSm.medisinskVurdering?.yrkesskade shouldBeEqualTo ocrFil.sykemeldinger.medisinskVurdering.isYrkesskade

            papirSm.skjermesForPasient shouldBeEqualTo ocrFil.sykemeldinger.medisinskVurdering.isSkjermesForPasient

            papirSm.perioder?.get(0)?.aktivitetIkkeMulig?.medisinskArsak?.beskrivelse!! shouldBeEqualTo "Han kan ikke gå rundt og være stasjonsmester med en pågående lungesykdom, i et miljø med kullfyrte tog"
            papirSm.perioder?.get(0)?.aktivitetIkkeMulig?.arbeidsrelatertArsak?.beskrivelse!! shouldBeEqualTo "Ordner seg!"
            papirSm.perioder?.get(1)?.gradert?.grad shouldBeEqualTo 50
            papirSm.perioder?.get(2)?.avventendeInnspillTilArbeidsgiver shouldBeEqualTo "Dere burde vurdere å skifte fra kulldrevne lokomotiver til moderne elektriske lokomotiver uten utslipp."
            papirSm.perioder?.get(3)?.behandlingsdager shouldBeEqualTo 30
            papirSm.perioder?.get(4)?.reisetilskudd shouldBeEqualTo true

            papirSm.prognose?.arbeidsforEtterPeriode shouldBeEqualTo true
            papirSm.prognose?.hensynArbeidsplassen shouldBeEqualTo "Dette vet jeg ikke hva betyr engang."
            papirSm.prognose?.erIArbeid?.egetArbeidPaSikt shouldBeEqualTo true
            papirSm.prognose?.erIArbeid?.annetArbeidPaSikt shouldBeEqualTo true
            papirSm.prognose?.erIArbeid?.vurderingsdato shouldBeEqualTo LocalDate.of(2020, 6, 1)
            papirSm.prognose?.erIkkeIArbeid?.vurderingsdato shouldBeEqualTo LocalDate.of(2020, 6, 1)
            papirSm.prognose?.erIkkeIArbeid?.arbeidsforPaSikt shouldBeEqualTo true

            papirSm.utdypendeOpplysninger?.size shouldBe 1
            papirSm.utdypendeOpplysninger?.containsKey("6.2") shouldBe true
            papirSm.utdypendeOpplysninger?.get("6.2")?.size shouldBeEqualTo 4
            papirSm.utdypendeOpplysninger?.get("6.2")?.containsKey("6.2.1") shouldBe true
            papirSm.utdypendeOpplysninger?.get("6.2")?.containsKey("6.2.2") shouldBe true
            papirSm.utdypendeOpplysninger?.get("6.2")?.containsKey("6.2.3") shouldBe true
            papirSm.utdypendeOpplysninger?.get("6.2")?.containsKey("6.2.4") shouldBe true
            papirSm.utdypendeOpplysninger?.get("6.2")?.get("6.2.4")?.sporsmal shouldBeEqualTo "Beskriv pågående og planlagt henvisning,utredning og/eller behandling"
            papirSm.utdypendeOpplysninger?.get("6.2")?.get("6.2.4")?.svar shouldBeEqualTo "Rolig ferie i kullfritt miljø"
            papirSm.utdypendeOpplysninger?.get("6.2")?.get("6.2.4")?.restriksjoner?.first() shouldBeEqualTo SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER

            papirSm.tiltakNAV shouldBeEqualTo "Stønad til ferie"
            papirSm.tiltakArbeidsplassen shouldBeEqualTo "Slutt med kulldrevne lokomotiver"
            papirSm.andreTiltak shouldBeEqualTo "Helsebringende opphold på Luster sanatorium"

            papirSm.meldingTilNAV?.bistandUmiddelbart shouldBeEqualTo true
            papirSm.meldingTilNAV?.beskrivBistand shouldBeEqualTo "Her må NAV få fingeren ut. Skandale!"
            papirSm.meldingTilArbeidsgiver shouldBeEqualTo "Ja, dere bør slutte med kull da."
            papirSm.kontaktMedPasient?.kontaktDato shouldBeEqualTo LocalDate.of(2000, 8, 10)
            papirSm.kontaktMedPasient?.begrunnelseIkkeKontakt shouldBeEqualTo "Han var syk i 2000 også."
            papirSm.behandletTidspunkt shouldBeEqualTo LocalDate.of(2020, 5, 2)

            papirSm.behandler?.hpr shouldBeEqualTo "100"
            papirSm.behandler?.tlf shouldBeEqualTo "103"
        }
    }
})
