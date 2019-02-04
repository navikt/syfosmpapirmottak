package no.nav.syfo.mapping

import no.nav.helse.sm2013.Address
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.DynaSvarType
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.NavnType
import no.nav.helse.sm2013.TeleCom
import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.syfo.SpmId
import java.time.LocalDateTime

fun mappapirsykemeldingtosm2013(papirSykemelding: SykemeldingerType): HelseOpplysningerArbeidsuforhet = HelseOpplysningerArbeidsuforhet().apply {
    syketilfelleStartDato = papirSykemelding.syketilfelleStartDato
    pasient = HelseOpplysningerArbeidsuforhet.Pasient().apply {
        fodselsnummer = Ident().apply {
            id = papirSykemelding.pasient.fnr
            typeId = CV().apply {
                dn = "Fødselsnummer"
                s = "2.16.578.1.12.4.1.1.8116"
                v = "FNR"
            }
        }
    }
    when (papirSykemelding.arbeidsgiver) {
        "1".toBigInteger() ->
                arbeidsgiver = HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                    harArbeidsgiver = CS().apply {
                        dn = "Én arbeidsgiver"
                        v = "1"
            }
                navnArbeidsgiver = papirSykemelding.arbeidsgiver.navnArbeidsgiver
                yrkesbetegnelse = papirSykemelding.arbeidsgiver.yrkesbetegnelse
                stillingsprosent = papirSykemelding.arbeidsgiver.stillingsprosent.toInt()
        }
    }

    if (papirSykemelding.medisinskVurdering != null) {
        medisinskVurdering = HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
            hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                diagnosekode = CV().apply {
                    dn = papirSykemelding.medisinskVurdering.hovedDiagnose.first().diagnosekode
                    s = papirSykemelding.medisinskVurdering.hovedDiagnose.first().diagnosekodeSystem
                    v = papirSykemelding.medisinskVurdering.hovedDiagnose.first().diagnose
                }
            }
            // TODO Legge til som bi diagnoser
            biDiagnoser = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.BiDiagnoser().apply {
                diagnosekode.add(
                        CV().apply {
                            dn = papirSykemelding.medisinskVurdering.bidiagnose.first().diagnosekode
                            s = papirSykemelding.medisinskVurdering.bidiagnose.first().diagnosekodeSystem
                            v = papirSykemelding.medisinskVurdering.bidiagnose.first().diagnose
                        }
                )
            }
                    // TODO legge til v og dn eller droppe heile feltet i papir
            annenFraversArsak = ArsakType().apply {
                arsakskode.add(CS().apply {
                    v = papirSykemelding.medisinskVurdering.annenFraversArsak
                    dn = "Helsetilstanden hindrer pasienten i å være i aktivitet" // TODO fylle ut
                })
                beskriv = papirSykemelding.medisinskVurdering.fraversBeskrivelse
                    }
                isSvangerskap = papirSykemelding.medisinskVurdering.svangerskap == "1".toBigInteger()
                isYrkesskade = papirSykemelding.medisinskVurdering.yrkesskade == "1".toBigInteger()
                yrkesskadeDato = papirSykemelding.medisinskVurdering.yrkesskadedato
                isSkjermesForPasient = papirSykemelding.medisinskVurdering.skjermesForPasient == "1".toBigInteger()
        }
    }

    aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
        periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
            periodeFOMDato = papirSykemelding.aktivitet.avventendeSykmelding.periodeFOMDato
            periodeTOMDato = papirSykemelding.aktivitet.avventendeSykmelding.periodeTOMDato
            avventendeSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AvventendeSykmelding().apply {
                innspillTilArbeidsgiver = papirSykemelding.aktivitet.innspillTilArbeidsgiver // TODO denne burde ligget i: papirSykemelding.aktivitet.avventendeSykmelding.innspillTilArbeidsgiver
            }
        periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
            periodeFOMDato = papirSykemelding.aktivitet.gradertSykmelding.periodeFOMDato
            periodeTOMDato = papirSykemelding.aktivitet.gradertSykmelding.periodeTOMDato
            gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                sykmeldingsgrad = papirSykemelding.aktivitet.gradertSykmelding.sykmeldingsgrad.toInt()
                isReisetilskudd = papirSykemelding.aktivitet.gradertSykmelding.reisetilskudd == "1".toBigInteger()
            }
        })
            periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = papirSykemelding.aktivitet.aktivitetIkkeMulig.periodeFOMDato
                periodeTOMDato = papirSykemelding.aktivitet.aktivitetIkkeMulig.periodeTOMDato
                aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                    medisinskeArsaker = ArsakType().apply {
                        // TODO hva skal vi fylle ut her???
                        arsakskode.add(CS().apply {
                            v = papirSykemelding.aktivitet.aktivitetIkkeMulig.medisinskeArsaker.medArsakerHindrer.toString()
                            dn = "" // TODO find at volven and map it
                            })
                        beskriv = papirSykemelding.aktivitet.aktivitetIkkeMulig.medisinskeArsaker.medArsakerBesk
                    }
                    arbeidsplassen = ArsakType().apply {
                        // TODO hva skal vi fylle ut her???
                        arsakskode.add(CS().apply {
                            v = papirSykemelding.aktivitet.aktivitetIkkeMulig.arbeidsplassen.arbeidsplassenHindrer.toString()
                            dn = "" // TODO find at volven and map it
                        })
                        beskriv = papirSykemelding.aktivitet.aktivitetIkkeMulig.arbeidsplassen.arbeidsplassenBesk
                    }
                }
            })
            periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = papirSykemelding.aktivitet.behandlingsdager.periodeFOMDato
                periodeFOMDato = papirSykemelding.aktivitet.behandlingsdager.periodeTOMDato
                behandlingsdager = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
                    antallBehandlingsdagerUke = papirSykemelding.aktivitet.behandlingsdager.antallBehandlingsdager.toInt()
                }
            })

            periode.add(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = papirSykemelding.aktivitet.reisetilskudd.periodeFOMDato
                periodeFOMDato = papirSykemelding.aktivitet.reisetilskudd.periodeTOMDato
            })
        }

        )
    }
    prognose = HelseOpplysningerArbeidsuforhet.Prognose().apply {
        isArbeidsforEtterEndtPeriode = papirSykemelding.prognose.friskmelding.arbeidsforEtterEndtPeriode == "1".toBigInteger()
        beskrivHensynArbeidsplassen = papirSykemelding.prognose.friskmelding.beskrivHensynArbeidsplassen
        erIArbeid = HelseOpplysningerArbeidsuforhet.Prognose.ErIArbeid().apply {
            isEgetArbeidPaSikt = papirSykemelding.prognose.medArbeidsgiver.tilbakeSammeArbeidsgiver == "1".toBigInteger()
            arbeidFraDato = papirSykemelding.prognose.medArbeidsgiver.tilbakeDato
            isAnnetArbeidPaSikt = papirSykemelding.prognose.medArbeidsgiver.tilbakeAnnenArbeidsgiver == "1".toBigInteger()
            vurderingDato = papirSykemelding.prognose.medArbeidsgiver.datoNyTilbakemelding
        }
        erIkkeIArbeid = HelseOpplysningerArbeidsuforhet.Prognose.ErIkkeIArbeid().apply {
            isArbeidsforPaSikt = papirSykemelding.prognose.utenArbeidsgiver.tilbakeArbeid == "1".toBigInteger()
            arbeidsforFraDato = papirSykemelding.prognose.utenArbeidsgiver.tilbakeDato
            vurderingDato = papirSykemelding.prognose.utenArbeidsgiver.datoNyTilbakemelding
        }
    }
    utdypendeOpplysninger = HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger().apply {
        spmGruppe.add(
                HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger.SpmGruppe().apply {
                    spmGruppeId = "6.2"
                    spmGruppeTekst = "Utdypende opplysninger ved 8,17 og 39 uker"
                    spmSvar.add(DynaSvarType().apply {
                        spmId = SpmId.SpmId6_2_1.spmId
                        spmTekst = SpmId.SpmId6_2_1.spmTekst
                        restriksjon = DynaSvarType.Restriksjon().apply {
                            restriksjonskode.add(CS().apply {
                                dn = SpmId.SpmId6_2_1.restriksjon.text
                                v = SpmId.SpmId6_2_1.restriksjon.codeValue
                            })
                        }
                        svarTekst = papirSykemelding.utdypendeOpplysninger.sykehistorie
                    })
                    spmSvar.add(DynaSvarType().apply {
                        spmId = SpmId.SpmId6_2_2.spmId
                        spmTekst = SpmId.SpmId6_2_2.spmTekst
                        restriksjon = DynaSvarType.Restriksjon().apply {
                            restriksjonskode.add(CS().apply {
                                dn = SpmId.SpmId6_2_2.restriksjon.text
                                v = SpmId.SpmId6_2_2.restriksjon.codeValue
                            })
                        }
                        svarTekst = papirSykemelding.utdypendeOpplysninger.arbeidsevne
                    })

                    spmSvar.add(DynaSvarType().apply {
                        spmId = SpmId.SpmId6_2_3.spmId
                        spmTekst = SpmId.SpmId6_2_3.spmTekst
                        restriksjon = DynaSvarType.Restriksjon().apply {
                            restriksjonskode.add(CS().apply {
                                dn = SpmId.SpmId6_2_3.restriksjon.text
                                v = SpmId.SpmId6_2_3.restriksjon.codeValue
                            })
                        }
                        svarTekst = papirSykemelding.utdypendeOpplysninger.behandlingsresultat
                    })

                    spmSvar.add(DynaSvarType().apply {
                        spmId = SpmId.SpmId6_2_4.spmId
                        spmTekst = SpmId.SpmId6_2_4.spmTekst
                        restriksjon = DynaSvarType.Restriksjon().apply {
                            restriksjonskode.add(CS().apply {
                                dn = SpmId.SpmId6_2_4.restriksjon.text
                                v = SpmId.SpmId6_2_4.restriksjon.codeValue
                            })
                        }
                        svarTekst = papirSykemelding.utdypendeOpplysninger.planlagtBehandling
                    })
                })
    }
    tiltak = HelseOpplysningerArbeidsuforhet.Tiltak().apply {
        tiltakArbeidsplassen = papirSykemelding.tiltak.tiltakArbeidsplassen
        tiltakNAV = papirSykemelding.tiltak.tiltakNAV
        andreTiltak = papirSykemelding.tiltak.andreTiltak
    }
    meldingTilNav = HelseOpplysningerArbeidsuforhet.MeldingTilNav().apply {
        isBistandNAVUmiddelbart = papirSykemelding.meldingTilNAV.bistandNAVUmiddelbart == "1".toBigInteger()
        beskrivBistandNAV = papirSykemelding.meldingTilNAV.beskrivBistandNAV
    }
    meldingTilArbeidsgiver = papirSykemelding.meldingTilArbeidsgiver
    kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
        kontaktDato = papirSykemelding.kontaktMedPasient.behandletDato
        begrunnIkkeKontakt = papirSykemelding.tilbakedatering.tilbakebegrunnelse
        behandletDato = LocalDateTime.of(papirSykemelding.tilbakedatering.tilbakeDato.year, papirSykemelding.tilbakedatering.tilbakeDato.month, papirSykemelding.tilbakedatering.tilbakeDato.dayOfMonth, 12, 0)
    }
    behandler = HelseOpplysningerArbeidsuforhet.Behandler().apply {
        navn = NavnType().apply {
            fornavn = ""
            etternavn = ""
        }
        id.add(Ident().apply {
            id = papirSykemelding.behandler.hpr.toString()
            typeId = CV().apply {
                dn = "HPR-numme"
                s = "2.16.578.1.12.4.1.1.8116"
                v = "HPR"
            }
        })
        adresse = Address().apply {
            type = CS().apply { }
            streetAdr = "" // TODO
            postalCode = "" // TODO
            city = "" // TODO
            postbox = "" // TODO
            county = CS().apply {
                // TODO
            }
            country = CS().apply {
                // TODO
            }
        }
        kontaktInfo.add(TeleCom().apply {
        })
    }
}