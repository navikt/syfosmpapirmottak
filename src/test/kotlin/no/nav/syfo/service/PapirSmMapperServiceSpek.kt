package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.client.getFileAsString
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.SvarRestriksjon
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.skanningMetadataUnmarshaller
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime

@KtorExperimentalAPI
object PapirSmMapperServiceSpek : Spek({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val dokumentInfoId = "123"
    val fnrPasient = "12345678910"
    val aktorId = "aktorId"
    val datoOpprettet = LocalDateTime.now()
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

    describe("PapirSmMappingService") {
        it("Tests a complete OCR file, and should parse fine") {
            val ocrFil = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/ocr-sykmelding-komplett.xml"))) as Skanningmetadata

            val papirSm = mapOcrFilTilPapirSmRegistrering(
                    journalpostId = journalpostId,
                    fnr = fnrPasient,
                    aktorId = aktorId,
                    dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet,
                    sykmeldingId = sykmeldingId,
                    sykmelder = null,
                    ocrFil = ocrFil
            )

            papirSm.journalpostId shouldEqual journalpostId
            papirSm.fnr shouldEqual fnrPasient
            papirSm.aktorId shouldEqual aktorId
            papirSm.dokumentInfoId shouldEqual dokumentInfoId
            papirSm.datoOpprettet shouldEqual datoOpprettet
            papirSm.sykmeldingId shouldEqual sykmeldingId
            papirSm.syketilfelleStartDato shouldEqual ocrFil.sykemeldinger.syketilfelleStartDato

            papirSm.arbeidsgiver?.harArbeidsgiver shouldEqual HarArbeidsgiver.EN_ARBEIDSGIVER
            papirSm.arbeidsgiver?.navn shouldEqual ocrFil.sykemeldinger.arbeidsgiver.navnArbeidsgiver
            papirSm.arbeidsgiver?.stillingsprosent shouldEqual ocrFil.sykemeldinger.arbeidsgiver.stillingsprosent.toInt()
            papirSm.arbeidsgiver?.yrkesbetegnelse shouldEqual ocrFil.sykemeldinger.arbeidsgiver.yrkesbetegnelse

            papirSm.medisinskVurdering?.hovedDiagnose?.kode shouldEqual ocrFil.sykemeldinger.medisinskVurdering.hovedDiagnose.first().diagnosekode
            papirSm.medisinskVurdering?.hovedDiagnose?.system shouldEqual Diagnosekoder.ICD10_CODE
            papirSm.medisinskVurdering?.hovedDiagnose?.tekst shouldEqual ocrFil.sykemeldinger.medisinskVurdering.hovedDiagnose.first().diagnose
            papirSm.medisinskVurdering?.biDiagnoser?.first()?.system shouldEqual Diagnosekoder.ICD10_CODE
            papirSm.medisinskVurdering?.annenFraversArsak?.beskrivelse shouldEqual ocrFil.sykemeldinger.medisinskVurdering.annenFraversArsak
            papirSm.medisinskVurdering?.svangerskap shouldEqual ocrFil.sykemeldinger.medisinskVurdering.isSvangerskap
            papirSm.medisinskVurdering?.yrkesskade shouldEqual ocrFil.sykemeldinger.medisinskVurdering.isYrkesskade

            papirSm.skjermesForPasient shouldEqual ocrFil.sykemeldinger.medisinskVurdering.isSkjermesForPasient

            papirSm.perioder?.get(0)?.aktivitetIkkeMulig?.medisinskArsak?.beskrivelse!! shouldEqual "Han kan ikke gå rundt og være stasjonsmester med en pågående lungesykdom, i et miljø med kullfyrte tog"
            papirSm.perioder?.get(0)?.aktivitetIkkeMulig?.arbeidsrelatertArsak?.beskrivelse!! shouldEqual "Ordner seg!"
            papirSm.perioder?.get(1)?.gradert?.grad shouldEqual 50
            papirSm.perioder?.get(2)?.avventendeInnspillTilArbeidsgiver shouldEqual "Dere burde vurdere å skifte fra kulldrevne lokomotiver til moderne elektriske lokomotiver uten utslipp."
            papirSm.perioder?.get(3)?.behandlingsdager shouldEqual 30
            papirSm.perioder?.get(4)?.reisetilskudd shouldEqual true

            papirSm.prognose?.arbeidsforEtterPeriode shouldEqual true
            papirSm.prognose?.hensynArbeidsplassen shouldEqual "Dette vet jeg ikke hva betyr engang."
            papirSm.prognose?.erIArbeid?.egetArbeidPaSikt shouldEqual true
            papirSm.prognose?.erIArbeid?.annetArbeidPaSikt shouldEqual true
            papirSm.prognose?.erIArbeid?.vurderingsdato shouldEqual LocalDate.of(2020, 6, 1)
            papirSm.prognose?.erIkkeIArbeid?.vurderingsdato shouldEqual LocalDate.of(2020, 6, 1)
            papirSm.prognose?.erIkkeIArbeid?.arbeidsforPaSikt shouldEqual true

            papirSm.utdypendeOpplysninger?.size shouldBe 1
            papirSm.utdypendeOpplysninger?.containsKey("6.2") shouldBe true
            papirSm.utdypendeOpplysninger?.get("6.2")?.size shouldEqual 4
            papirSm.utdypendeOpplysninger?.get("6.2")?.containsKey("6.2.1") shouldBe true
            papirSm.utdypendeOpplysninger?.get("6.2")?.containsKey("6.2.2") shouldBe true
            papirSm.utdypendeOpplysninger?.get("6.2")?.containsKey("6.2.3") shouldBe true
            papirSm.utdypendeOpplysninger?.get("6.2")?.containsKey("6.2.4") shouldBe true
            papirSm.utdypendeOpplysninger?.get("6.2")?.get("6.2.4")?.sporsmal shouldEqual "Beskriv pågående og planlagt henvisning,utredning og/eller behandling"
            papirSm.utdypendeOpplysninger?.get("6.2")?.get("6.2.4")?.svar shouldEqual "Rolig ferie i kullfritt miljø"
            papirSm.utdypendeOpplysninger?.get("6.2")?.get("6.2.4")?.restriksjoner?.first() shouldEqual SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER

            papirSm.tiltakNAV shouldEqual "Stønad til ferie"
            papirSm.tiltakArbeidsplassen shouldEqual "Slutt med kulldrevne lokomotiver"
            papirSm.andreTiltak shouldEqual "Helsebringende opphold på Luster sanatorium"

            papirSm.meldingTilNAV?.bistandUmiddelbart shouldEqual true
            papirSm.meldingTilNAV?.beskrivBistand shouldEqual "Her må NAV få fingeren ut. Skandale!"
            papirSm.meldingTilArbeidsgiver shouldEqual "Ja, dere bør slutte med kull da."
            papirSm.kontaktMedPasient?.kontaktDato shouldEqual LocalDate.of(2020,5,2)
            papirSm.behandletTidspunkt shouldEqual LocalDate.of(2020,5,2)

            papirSm.behandler?.hpr shouldEqual "100"
            papirSm.behandler?.tlf shouldEqual "103"
        }

    }

})
