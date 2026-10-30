package no.nav.syfo.service

import io.kotest.core.spec.style.FunSpec
import java.math.BigInteger
import java.time.LocalDate
import no.nav.helse.papirsykemelding.AktivitetIkkeMuligType
import no.nav.helse.papirsykemelding.AktivitetType
import no.nav.helse.papirsykemelding.BehandlerType
import no.nav.helse.papirsykemelding.HovedDiagnoseType
import no.nav.helse.papirsykemelding.MedisinskVurderingType
import no.nav.helse.papirsykemelding.PasientType
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.helse.papirsykemelding.SykemeldingerType
import org.amshove.kluent.shouldBeEqualTo

class SkanningmetadataSammenligningSpek :
    FunSpec({
        fun skanningmetadata(
            fnr: String? = "12345678910",
            diagnosekode: String = "L87",
            tom: LocalDate = LocalDate.of(2026, 1, 14),
            hpr: BigInteger? = BigInteger.valueOf(1234567),
        ) =
            Skanningmetadata().apply {
                sykemeldinger =
                    SykemeldingerType().apply {
                        pasient = PasientType().apply { this.fnr = fnr }
                        medisinskVurdering =
                            MedisinskVurderingType().apply {
                                hovedDiagnose.add(
                                    HovedDiagnoseType().apply {
                                        diagnosekodeSystem = "ICPC2"
                                        this.diagnosekode = diagnosekode
                                    }
                                )
                            }
                        aktivitet =
                            AktivitetType().apply {
                                aktivitetIkkeMulig =
                                    AktivitetIkkeMuligType().apply {
                                        periodeFOMDato = LocalDate.of(2026, 1, 1)
                                        periodeTOMDato = tom
                                    }
                            }
                        behandler = BehandlerType().apply { this.hpr = hpr }
                    }
            }

        test("Like skanningmetadata gir ingen avvik") {
            sammenlignSkanningmetadata(skanningmetadata(), skanningmetadata()) shouldBeEqualTo
                emptyList()
        }

        test("Blank streng og null regnes som likt") {
            sammenlignSkanningmetadata(
                skanningmetadata(fnr = " "),
                skanningmetadata(fnr = null),
            ) shouldBeEqualTo emptyList()
        }

        test("Returnerer feltene som avviker") {
            sammenlignSkanningmetadata(
                skanningmetadata(),
                skanningmetadata(diagnosekode = "L88", tom = LocalDate.of(2026, 1, 15), hpr = null),
            ) shouldBeEqualTo
                listOf(
                    OcrAvvik("medisinskVurdering.hovedDiagnose.diagnosekode", "L87", "L88"),
                    OcrAvvik(
                        "aktivitet.aktivitetIkkeMulig.periodeTOMDato",
                        "2026-01-14",
                        "2026-01-15",
                    ),
                    OcrAvvik("behandler.hpr", "1234567", null),
                )
        }

        test("Manglende aktivitet regnes som avvik") {
            val utenAktivitet = skanningmetadata().apply { sykemeldinger.aktivitet = null }
            sammenlignSkanningmetadata(skanningmetadata(), utenAktivitet) shouldBeEqualTo
                listOf(
                    OcrAvvik("aktivitet.aktivitetIkkeMulig", "true", "false"),
                    OcrAvvik("aktivitet.aktivitetIkkeMulig.periodeFOMDato", "2026-01-01", null),
                    OcrAvvik("aktivitet.aktivitetIkkeMulig.periodeTOMDato", "2026-01-14", null),
                )
        }
    })
