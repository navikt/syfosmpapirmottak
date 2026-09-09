package no.nav.syfo.service

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.OcrParserAktivitet
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue

/**
 * Regresjonstest for wire-kontrakten mot sykmelding-ocr-parser sin `/api/parse`. Tjenesten svarer
 * med en JSON-array av `type`-taggede konvolutter (Success/Unsupported/Failure). Fordi klienten
 * bruker Jackson må de sealed-typene ha Jackson-polymorfi (`@JsonTypeInfo`/`@JsonSubTypes`) —
 * kotlinx `@SerialName` blir ignorert av Jackson. Se navikt/sykmelding-ocr-parser
 * docs/syfosmpapirmottak-ocr-client-fix.md.
 */
class OcrParserParseResponseDeserializationTest :
    FunSpec({
        val objectMapper = jacksonMapperBuilder().build()

        test("deserialiserer JSON-array av type-taggede konvolutter og polymorf aktivitet") {
            val json =
                """
                [
                  {
                    "type": "Success",
                    "sykmelding": {
                      "formType": "NAV 08-07.04",
                      "formVersion": "1",
                      "vurderingType": "FORSTE_VURDERING",
                      "legemeldtFravaerStart": "2024-01-01",
                      "pasient": {},
                      "arbeidsgiver": {},
                      "diagnose": {},
                      "aktiviteter": [
                        { "type": "Gradert", "fom": "2024-01-01", "tom": "2024-01-14", "grad": 50 }
                      ],
                      "prognose": {},
                      "tilleggsinformasjon": {},
                      "tilbakedatering": {},
                      "sykmelder": {},
                      "meta": { "parserId": "p1", "warnings": [] }
                    }
                  }
                ]
                """
                    .trimIndent()

            val responses: List<OcrParserParseResponse> = objectMapper.readValue(json)

            responses.size shouldBeEqualTo 1
            val success = responses.first() as OcrParserParseResponse.Success
            val aktivitet = success.sykmelding.aktiviteter.single()
            aktivitet.shouldBeInstanceOf<OcrParserAktivitet.Gradert>()
            (aktivitet as OcrParserAktivitet.Gradert).grad shouldBeEqualTo 50
        }

        test("deserialiserer Failure-konvolutt") {
            val json = """[ { "type": "Failure", "reason": "ingen sykmelding funnet" } ]"""

            val responses: List<OcrParserParseResponse> = objectMapper.readValue(json)

            val failure = responses.single() as OcrParserParseResponse.Failure
            failure.reason shouldBeEqualTo "ingen sykmelding funnet"
        }
    })
