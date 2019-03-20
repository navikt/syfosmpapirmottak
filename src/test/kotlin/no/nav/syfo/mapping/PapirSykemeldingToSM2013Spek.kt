package no.nav.syfo.mapping

import no.nav.helse.sykSkanningMeta.AktivitetIkkeMuligType
import no.nav.helse.sykSkanningMeta.AktivitetType
import no.nav.helse.sykSkanningMeta.ArbeidsgiverType
import no.nav.helse.sykSkanningMeta.ArbeidsplassenType
import no.nav.helse.sykSkanningMeta.AvventendeSykmeldingType
import no.nav.helse.sykSkanningMeta.BehandlerType
import no.nav.helse.sykSkanningMeta.BehandlingsdagerType
import no.nav.helse.sykSkanningMeta.BidiagnoseType
import no.nav.helse.sykSkanningMeta.FriskmeldingType
import no.nav.helse.sykSkanningMeta.GradertSykmeldingType
import no.nav.helse.sykSkanningMeta.HovedDiagnoseType
import no.nav.helse.sykSkanningMeta.KontaktMedPasientType
import no.nav.helse.sykSkanningMeta.MedArbeidsgiverType
import no.nav.helse.sykSkanningMeta.MedisinskVurderingType
import no.nav.helse.sykSkanningMeta.MedisinskeArsakerType
import no.nav.helse.sykSkanningMeta.MeldingTilNAVType
import no.nav.helse.sykSkanningMeta.PasientType
import no.nav.helse.sykSkanningMeta.PrognoseType
import no.nav.helse.sykSkanningMeta.ReisetilskuddType
import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.helse.sykSkanningMeta.TilbakedateringType
import no.nav.helse.sykSkanningMeta.TiltakType
import no.nav.helse.sykSkanningMeta.UtdypendeOpplysningerType
import no.nav.helse.sykSkanningMeta.UtenArbeidsgiverType
import no.nav.syfo.model.toSykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.UUID

object PapirSykemeldingToSM2013Spek : Spek({

    describe("Cheking mapping results") {
        it("Should map hpr from papirsykemelding to sykemlding format") {
            val papirSykmelding = SykemeldingerType().apply {
                syketilfelleStartDato = LocalDate.now()
                pasient = PasientType().apply {
                    fnr = "3006310441"
                    }
                    arbeidsgiver = ArbeidsgiverType().apply {
                        harArbeidsgiver = "1"
                        navnArbeidsgiver = "Oslo kommune"
                        yrkesbetegnelse = "Utvikler"
                        stillingsprosent = "100".toBigInteger()
                    }
                medisinskVurdering = MedisinskVurderingType().apply {
                    hovedDiagnose.add(HovedDiagnoseType().apply {
                        diagnosekodeSystem = "2.16.578.1.12.4.1.1.7170"
                        diagnosekode = "Problem med jus/politi"
                        diagnose = "A09"
                    })
                    bidiagnose.add(BidiagnoseType().apply {
                        diagnosekodeSystem = "2.16.578.1.12.4.1.1.7170"
                        diagnosekode = "Problem med jus/politi"
                        diagnose = "A08"
                    })

                    annenFraversArsak = "Kunne ikkje komme på jobb pga sykt barn"
                    fraversBeskrivelse = "Barn var sykt"
                    svangerskap = "0".toBigInteger()
                    yrkesskade = "0".toBigInteger()
                    yrkesskadedato = LocalDate.now()
                    skjermesForPasient = 0.toBigInteger()
                }
                aktivitet = AktivitetType().apply {
                    avventendeSykmelding = AvventendeSykmeldingType().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusMonths(1)
                    }
                    innspillTilArbeidsgiver = "Han kan ikkje jobbe med tunge oppgaver"
                    gradertSykmelding = GradertSykmeldingType().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusMonths(1)
                        sykmeldingsgrad = "0"
                        reisetilskudd = "0".toBigInteger()
                    }
                    aktivitetIkkeMulig = AktivitetIkkeMuligType().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusMonths(1)
                        medisinskeArsaker = MedisinskeArsakerType().apply {
                            medArsakerHindrer = "1".toBigInteger()
                            medArsakerBesk = "Kan jobbe"
                        }
                        arbeidsplassen = ArbeidsplassenType().apply {
                            arbeidsplassenHindrer = "1".toBigInteger()
                            arbeidsplassenBesk = "Kan jobbe"
                        }
                    }
                    behandlingsdager = BehandlingsdagerType().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusDays(10)
                        antallBehandlingsdager = "0".toBigInteger()
                    }
                    reisetilskudd = ReisetilskuddType().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusDays(14)
                    }
                }
                prognose = PrognoseType().apply {
                    friskmelding = FriskmeldingType().apply {
                        arbeidsforEtterEndtPeriode = "0".toBigInteger()
                        beskrivHensynArbeidsplassen = "Kan ikkje, ta tunge løft"
                    }
                    medArbeidsgiver = MedArbeidsgiverType().apply {
                        tilbakeSammeArbeidsgiver = "0".toBigInteger()
                        tilbakeDato = LocalDate.now()
                        tilbakeAnnenArbeidsgiver = "0".toBigInteger()
                        datoNyTilbakemelding = LocalDate.now()
                    }
                    utenArbeidsgiver = UtenArbeidsgiverType().apply {
                        tilbakeArbeid = "0".toBigInteger()
                        tilbakeDato = LocalDate.now()
                        datoNyTilbakemelding = LocalDate.now().plusDays(3)
                    }
                }
                utdypendeOpplysninger = UtdypendeOpplysningerType().apply {
                    sykehistorie = "Har vært mye syk før"
                    arbeidsevne = "Kan jobbe litt"
                    behandlingsresultat = "Meget posetive resultater"
                    planlagtBehandling = "Ny runde med fysio"
                }
                tiltak = TiltakType().apply {
                    tiltakArbeidsplassen = "Ikkje tunge arbeids oppgaver"
                    tiltakNAV = "Ergonomisk stol"
                    andreTiltak = "Jobbe lettere"
                }
                meldingTilNAV = MeldingTilNAVType().apply {
                    bistandNAVUmiddelbart = "0".toBigInteger()
                    beskrivBistandNAV = "Trenger bistand"
                }
                meldingTilArbeidsgiver = "Han må jobbe med lettere arbeids oppgaver"
                tilbakedatering = TilbakedateringType().apply {
                    tilbakeDato = LocalDate.now()
                    tilbakebegrunnelse = "Pasient var syk"
                }
                kontaktMedPasient = KontaktMedPasientType().apply {
                    behandletDato = LocalDate.now()
                }
                behandler = BehandlerType().apply {
                    hpr = "213123".toBigInteger()
                    telefon = "99999999".toBigInteger()
                    adresse = "Sannergata 2, 0112 Oslo"
                }
                }

            val sykmelding = papirSykmelding.toSykmelding(
                    sykmeldingId = UUID.randomUUID().toString(),
                    pasientAktoerId = "",
                    legeAktoerId = "",
                    msgId = UUID.randomUUID().toString()
            )

            sykmelding.behandler.hpr shouldEqual papirSykmelding.behandler.hpr.toString()
            }
    }
})