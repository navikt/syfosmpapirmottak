package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.util.UUID
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
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.get
import no.nav.syfo.util.skanningMetadataUnmarshaller
import org.amshove.kluent.should
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.amshove.kluent.shouldNotThrow
import org.amshove.kluent.shouldThrow
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

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
        it("Should work") {
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

            // Todo: Trenger et fullverdig eksempel på en OCR-melding for å kunne teste helheten.
            // TODO: Ikke ferdig

        }

    }

})
