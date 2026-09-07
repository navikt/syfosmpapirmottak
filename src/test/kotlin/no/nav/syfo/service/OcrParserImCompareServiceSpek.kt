package no.nav.syfo.service

import io.kotest.core.spec.style.FunSpec
import java.math.BigInteger
import java.time.LocalDate
import no.nav.helse.papirsykemelding.AktivitetType
import no.nav.helse.papirsykemelding.ArbeidsgiverType
import no.nav.helse.papirsykemelding.BehandlerType
import no.nav.helse.papirsykemelding.FriskmeldingType
import no.nav.helse.papirsykemelding.GradertSykmeldingType
import no.nav.helse.papirsykemelding.HovedDiagnoseType
import no.nav.helse.papirsykemelding.KontaktMedPasientType
import no.nav.helse.papirsykemelding.MedisinskVurderingType
import no.nav.helse.papirsykemelding.PasientType
import no.nav.helse.papirsykemelding.PrognoseType
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.helse.papirsykemelding.SykemeldingerType
import no.nav.syfo.metrics.OCR_SAMMENLIGNING_DOKUMENT
import no.nav.syfo.metrics.OCR_SAMMENLIGNING_FELT
import no.nav.syfo.model.OcrParserAktivitet
import no.nav.syfo.model.OcrParserArbeidsgiver
import no.nav.syfo.model.OcrParserDiagnose
import no.nav.syfo.model.OcrParserPasient
import no.nav.syfo.model.OcrParserPrognose
import no.nav.syfo.model.OcrParserSykmelder
import no.nav.syfo.model.OcrParserSykmelding
import no.nav.syfo.model.OcrParserSykmeldingMeta
import no.nav.syfo.model.OcrParserTilbakedatering
import no.nav.syfo.model.OcrParserTilleggsinformasjon
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain

class OcrParserImCompareServiceSpek :
    FunSpec({
        val service = OcrParserImCompareService()

        fun nyttOcr() =
            OcrParserSykmelding(
                formType = "NAV 08-07.04",
                formVersion = "1",
                vurderingType = null,
                legemeldtFravaerStart = "01.02.2024",
                pasient = OcrParserPasient(fnr = "12345678910"),
                arbeidsgiver =
                    OcrParserArbeidsgiver(
                        navn = "Bedrift AS",
                        yrkeStilling = "Snekker",
                        stillingsprosent = 50,
                        flereArbeidsgivere = false,
                    ),
                diagnose =
                    OcrParserDiagnose(
                        hoveddiagnoseKodesystem = "ICD-10",
                        hoveddiagnoseKode = "M79",
                        hoveddiagnoseTekst = "Ryggsmerter",
                        svangerskap = false,
                        yrkesskade = false,
                    ),
                aktiviteter =
                    listOf(
                        OcrParserAktivitet.Gradert(
                            fom = "01.02.2024",
                            tom = "15.02.2024",
                            grad = 60,
                            reisetilskudd = false,
                        )
                    ),
                prognose = OcrParserPrognose(arbeidsforEtterPeriode = true),
                tilleggsinformasjon = OcrParserTilleggsinformasjon(bistandNavOnskes = false),
                tilbakedatering = OcrParserTilbakedatering(kontaktDato = "15.01.2024"),
                sykmelder =
                    OcrParserSykmelder(
                        dato = "15.01.2024",
                        navn = "Lege Legesen",
                        hprNummer = "123456",
                        telefon = "99887766",
                    ),
                meta = OcrParserSykmeldingMeta(formId = null, formVersion = "1", parserId = "test"),
            )

        fun gammelOcr() =
            Skanningmetadata().apply {
                sykemeldinger =
                    SykemeldingerType().apply {
                        syketilfelleStartDato = LocalDate.of(2024, 2, 1)
                        pasient = PasientType().apply { fnr = "12345678910" }
                        arbeidsgiver =
                            ArbeidsgiverType().apply {
                                navnArbeidsgiver = "Bedrift AS"
                                yrkesbetegnelse = "Snekker"
                                stillingsprosent = BigInteger.valueOf(50)
                                harArbeidsgiver = "Én arbeidsgiver"
                            }
                        medisinskVurdering =
                            MedisinskVurderingType().apply {
                                // Avvik: annen hoveddiagnosekode enn nytt OCR-resultat
                                hovedDiagnose.add(
                                    HovedDiagnoseType().apply {
                                        diagnosekodeSystem = "ICD-10"
                                        diagnosekode = "M545"
                                        // diagnose (tekst) er null -> demonstrerer "kun ny"
                                    }
                                )
                                isSvangerskap = false
                                isYrkesskade = false
                            }
                        aktivitet =
                            AktivitetType().apply {
                                gradertSykmelding =
                                    GradertSykmeldingType().apply {
                                        periodeFOMDato = LocalDate.of(2024, 2, 1)
                                        periodeTOMDato = LocalDate.of(2024, 2, 15)
                                        sykmeldingsgrad = "60"
                                        isReisetilskudd = false
                                    }
                            }
                        prognose =
                            PrognoseType().apply {
                                friskmelding =
                                    FriskmeldingType().apply { isArbeidsforEtterEndtPeriode = true }
                            }
                        tilbakedatering = null
                        kontaktMedPasient =
                            KontaktMedPasientType().apply {
                                behandletDato = LocalDate.of(2024, 1, 15)
                            }
                        behandler =
                            BehandlerType().apply {
                                hpr = BigInteger.valueOf(123456)
                                telefon = BigInteger.valueOf(99887766)
                            }
                    }
            }

        context("OcrParserImCompareService") {
            test("bygger sammenligningslogg og skriver den ut") {
                val felter = service.sammenlignFelter(nyttOcr(), gammelOcr())
                val logg = service.byggSammenligningsLogg(felter, "1234", "123")

                // Skriv ut faktisk loggmelding slik at vi kan vurdere formatet manuelt.
                println(logg)

                // Fnr er likt paa begge sider.
                felter.first { it.felt == "pasient.fnr" }.lik shouldBeEqualTo true
                // Hoveddiagnosekode er ulik (M79 vs M545).
                felter.first { it.felt == "diagnose.hoveddiagnoseKode" }.lik shouldBeEqualTo false
                // Gradert sykmelding er tilstede paa begge sider.
                felter.first { it.felt == "aktivitet.gradert (tilstede)" }.lik shouldBeEqualTo true
                // Datofelt (string dd.MM.yyyy vs LocalDate) skal matche.
                felter.first { it.felt == "legemeldtFravaerStart" }.lik shouldBeEqualTo true

                logg shouldContain "OCR-sammenligning sykmeldingId=1234 journalpostId=123"
                logg shouldContain "AVVIK"
                logg shouldContain "LIK"
            }

            test("compare oppdaterer metrics-tellere") {
                val fnrLikFoer = OCR_SAMMENLIGNING_FELT.labels("pasient.fnr", "lik").get()
                val diagnoseAvvikFoer =
                    OCR_SAMMENLIGNING_FELT.labels("diagnose.hoveddiagnoseKode", "avvik").get()
                val dokumentAvvikFoer = OCR_SAMMENLIGNING_DOKUMENT.labels("har_avvik").get()

                service.compare(nyttOcr(), gammelOcr(), "1234", "123")

                // Likt felt telles som "lik".
                OCR_SAMMENLIGNING_FELT.labels("pasient.fnr", "lik").get() shouldBeEqualTo
                    fnrLikFoer + 1.0
                // Ulikt felt telles som "avvik".
                OCR_SAMMENLIGNING_FELT.labels("diagnose.hoveddiagnoseKode", "avvik")
                    .get() shouldBeEqualTo diagnoseAvvikFoer + 1.0
                // Dokumentet har minst ett avvik.
                OCR_SAMMENLIGNING_DOKUMENT.labels("har_avvik").get() shouldBeEqualTo
                    dokumentAvvikFoer + 1.0
            }
        }
    })
