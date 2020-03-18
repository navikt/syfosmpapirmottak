package no.nav.syfo.service

import java.time.LocalDateTime
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLCS
import no.nav.helse.msgHead.XMLCV
import no.nav.helse.msgHead.XMLDocument
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLMsgInfo
import no.nav.helse.msgHead.XMLOrganisation
import no.nav.helse.msgHead.XMLReceiver
import no.nav.helse.msgHead.XMLRefDoc
import no.nav.helse.msgHead.XMLSender
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sykSkanningMeta.ArbeidsgiverType
import no.nav.helse.sykSkanningMeta.MedisinskVurderingType
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.log
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.util.LoggingMeta

fun mapOcrFilTilFellesformat(
    skanningmetadata: Skanningmetadata,
    fnr: String,
    datoOpprettet: LocalDateTime,
    sykmelder: Sykmelder,
    sykmeldingId: String,
    loggingMeta: LoggingMeta
): XMLEIFellesformat {
    if (skanningmetadata.sykemeldinger.pasient.fnr != fnr) {
        log.error("Fnr fra sykmelding matcher ikke fnr fra journalposthendelsen, avbryter.. {}", fields(loggingMeta))
        throw IllegalStateException("Fnr fra sykmelding matcher ikke fnr fra journalposthendelsen")
    }

    return XMLEIFellesformat().apply {
        any.add(XMLMsgHead().apply {
            msgInfo = XMLMsgInfo().apply {
                type = XMLCS().apply {
                    dn = "Medisinsk vurdering av arbeidsmulighet ved sykdom, sykmelding"
                    v = "SYKMELD"
                }
                miGversion = "v1.2 2006-05-24"
                genDate = datoOpprettet
                msgId = sykmeldingId
                ack = XMLCS().apply {
                    dn = "Ja"
                    v = "J"
                }
                sender = XMLSender().apply {
                    comMethod = XMLCS().apply {
                        dn = "EDI"
                        v = "EDI"
                    }
                    organisation = XMLOrganisation().apply {
                        healthcareProfessional = XMLHealthcareProfessional().apply {
                            givenName = sykmelder.fornavn
                            middleName = sykmelder.mellomnavn
                            familyName = sykmelder.etternavn
                            ident.addAll(listOf(
                                    XMLIdent().apply {
                                        id = sykmelder.hprNummer
                                        typeId = XMLCV().apply {
                                            dn = "HPR-nummer"
                                            s = "6.87.654.3.21.9.8.7.6543.2198"
                                            v = "HPR"
                                        }
                                    },
                                    XMLIdent().apply {
                                        id = sykmelder.fnr
                                        typeId = XMLCV().apply {
                                            dn = "Fødselsnummer"
                                            s = "2.16.578.1.12.4.1.1.8327"
                                            v = "FNR"
                                        }
                                    }))
                        }
                    }
                }
                receiver = XMLReceiver().apply {
                    comMethod = XMLCS().apply {
                        dn = "EDI"
                        v = "EDI"
                    }
                    organisation = XMLOrganisation().apply {
                        organisationName = "NAV"
                        ident.addAll(listOf(
                                XMLIdent().apply {
                                    id = "79768"
                                    typeId = XMLCV().apply {
                                        dn = "Identifikator fra Helsetjenesteenhetsregisteret (HER-id)"
                                        s = "2.16.578.1.12.4.1.1.9051"
                                        v = "HER"
                                    }
                                },
                                XMLIdent().apply {
                                    id = "889640782"
                                    typeId = XMLCV().apply {
                                        dn = "Organisasjonsnummeret i Enhetsregister (Brønøysund)"
                                        s = "2.16.578.1.12.4.1.1.9051"
                                        v = "ENH"
                                    }
                                }))
                    }
                }
            }
            document.add(XMLDocument().apply {
                refDoc = XMLRefDoc().apply {
                    msgType = XMLCS().apply {
                        dn = "XML-instans"
                        v = "XML"
                    }
                    content = XMLRefDoc.Content().apply {
                        any.add(HelseOpplysningerArbeidsuforhet().apply {
                            syketilfelleStartDato = skanningmetadata.sykemeldinger.syketilfelleStartDato
                            pasient = HelseOpplysningerArbeidsuforhet.Pasient().apply {
                                fodselsnummer = Ident().apply {
                                    id = skanningmetadata.sykemeldinger.pasient.fnr
                                    typeId = CV().apply {
                                        dn = "Fødselsnummer"
                                        s = "2.16.578.1.12.4.1.1.8116"
                                        v = "FNR"
                                    }
                                }
                            }
                            arbeidsgiver = HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                                harArbeidsgiver = tilArbeidsgiver(skanningmetadata.sykemeldinger.arbeidsgiver)
                            }
                            medisinskVurdering = tilMedisinskVurdering(skanningmetadata.sykemeldinger.medisinskVurdering)
                        })
                    }
                }
            })
        })
    }
}

fun tilArbeidsgiver(arbeidsgiverType: ArbeidsgiverType?): CS =
        when {
            arbeidsgiverType?.harArbeidsgiver == "Flere arbeidsgivere" -> CS().apply {
                dn = "Flere arbeidsgivere"
                v = "2"
            }
            arbeidsgiverType?.harArbeidsgiver == "Flerearbeidsgivere" -> CS().apply {
                dn = "Flere arbeidsgivere"
                v = "2"
            }
            arbeidsgiverType?.harArbeidsgiver == "En arbeidsgiver" -> CS().apply {
                dn = "Én arbeidsgiver"
                v = "1"
            }
            arbeidsgiverType?.harArbeidsgiver == "Ingen arbeidsgiver" -> CS().apply {
                dn = "Ingen arbeidsgiver"
                v = "3"
            }
            else -> {
                log.warn("Klarte ikke å mappe {} til riktig harArbeidsgiver-verdi, bruker en arbeidsgiver som standard, {}", arbeidsgiverType?.harArbeidsgiver)
                CS().apply {
                    dn = "Én arbeidsgiver"
                    v = "1"
                }
            }
        }

fun tilMedisinskVurdering(medisinskVurderingType: MedisinskVurderingType): HelseOpplysningerArbeidsuforhet.MedisinskVurdering {
    if (medisinskVurderingType.hovedDiagnose.isNullOrEmpty() && medisinskVurderingType.annenFraversArsak.isNullOrEmpty()) {
        log.warn("Sykmelding mangler hoveddiagnose og annenFraversArsak, avbryter..")
        throw IllegalStateException("Sykmelding mangler hoveddiagnose")
    }

    val biDiagnoseListe: List<CV>? = medisinskVurderingType.bidiagnose?.map {
        toMedisinskVurderingDiagnode(it.diagnosekode)
    }

    return HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
        if (medisinskVurderingType.hovedDiagnose.isNullOrEmpty()) {
            hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                toMedisinskVurderingDiagnode(medisinskVurderingType.hovedDiagnose[0].diagnosekode)
            }
        }
        if (biDiagnoseListe != null && biDiagnoseListe.isNotEmpty()) {
            biDiagnoser.diagnosekode.addAll(biDiagnoseListe)
        }
        isSkjermesForPasient = medisinskVurderingType.isSkjermesForPasient
        if (!medisinskVurderingType.annenFraversArsak.isNullOrEmpty()) {
            annenFraversArsak = ArsakType().apply {
                arsakskode.add(CS())
                beskriv = medisinskVurderingType.annenFraversArsak
            }
        }
        isSvangerskap = medisinskVurderingType.isSvangerskap
        isYrkesskade = medisinskVurderingType.isYrkesskade
    }
}

fun toMedisinskVurderingDiagnode(originalDiagnosekode: String): CV {
    val diagnosekode = if (originalDiagnosekode.contains(".")) {
        originalDiagnosekode.replace(".", "")
    } else {
        originalDiagnosekode
    }
    if (Diagnosekoder.icd10.containsKey(diagnosekode)) {
        log.info("Mappet $originalDiagnosekode til $diagnosekode for ICD10, {}")
        return CV().apply {
            s = Diagnosekoder.ICD10_CODE
            v = diagnosekode
            dn = Diagnosekoder.icd10[diagnosekode]?.text ?: ""
        }
    } else if (Diagnosekoder.icpc2.containsKey(diagnosekode)) {
        log.info("Mappet $originalDiagnosekode til $diagnosekode for ICPC2, {}")
        return CV().apply {
            s = Diagnosekoder.ICPC2_CODE
            v = diagnosekode
            dn = Diagnosekoder.icpc2[diagnosekode]?.text ?: ""
        }
    }
    log.warn("Diagnosekode $originalDiagnosekode tilhører ingen kjente kodeverk, {}")
    throw IllegalStateException("Diagnosekode $originalDiagnosekode tilhører ingen kjente kodeverk")
}
