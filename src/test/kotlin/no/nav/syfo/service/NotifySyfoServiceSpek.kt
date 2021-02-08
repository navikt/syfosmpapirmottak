package no.nav.syfo.service

import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.client.getFileAsString
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.skanningMetadataUnmarshaller
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

object NotifySyfoServiceSpek : Spek({
    describe("Legger sykmelding på kø til syfoservice") {
        it("Sykmeldingen Base64-encodes på UTF-8 format") {
            val sykmeldingId = "1234"
            val journalpostId = "123"
            val fnrLege = "fnrLege"
            val aktorIdLege = "aktorIdLege"
            val hprNummer = "10052512"
            val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

            val skanningMetadata = skanningMetadataUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/ocr-sykmelding.xml"))) as Skanningmetadata
            val sykmelder = Sykmelder(hprNummer = hprNummer, fnr = fnrLege, aktorId = aktorIdLege, fornavn = "Fornavn", mellomnavn = null, etternavn = "Bodø", telefonnummer = null)
            val pdlPerson = PdlPerson(Navn("fornavn", "mellomnavn", "etternavn"), "12345678910", "aktorid", null)

            val fellesformat = mapOcrFilTilFellesformat(
                skanningmetadata = skanningMetadata,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                loggingMeta = loggingMetadata,
                pdlPerson = pdlPerson,
                journalpostId = journalpostId
            )

            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)

            val sykemeldingsBytes = convertSykemeldingToBase64(healthInformation)

            val sykmelding = String(sykemeldingsBytes, Charsets.UTF_8)
            sykmelding.contains("Bodø") shouldBeEqualTo true
        }
    }
})
