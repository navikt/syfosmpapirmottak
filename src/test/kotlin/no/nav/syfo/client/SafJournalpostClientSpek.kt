package no.nav.syfo.client

import io.kotest.core.spec.style.FunSpec
import java.time.Month
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo

class SafJournalpostClientSpek :
    FunSpec({
        val loggingMetadata = LoggingMeta("sykmeldingId", "123", "hendelsesId")

        context("finnDokumentIdForOcr fungerer som den skal") {
            test("Henter riktig dokumentId for happy-case") {
                val dokumentListe: List<Dokument> =
                    listOf(
                        Dokument(
                            "test-tittel",
                            "dokumentinfo",
                            "brevkode",
                            listOf(
                                Dokumentvarianter(Variantformat.ARKIV),
                            )
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdOriginal",
                            "brevkode",
                            listOf(Dokumentvarianter(Variantformat.ORIGINAL)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoId",
                            "brevkode",
                            emptyList(),
                        ),
                    )

                val dokumentId = finnDokumentIdForOcr(dokumentListe, loggingMetadata)

                dokumentId shouldBeEqualTo "dokumentInfoIdOriginal"
            }

            test("Returnerer null hvis dokumentListe er tom") {
                val dokumentListe: List<Dokument> = emptyList()

                val dokumentId = finnDokumentIdForOcr(dokumentListe, loggingMetadata)

                dokumentId shouldBeEqualTo null
            }

            test("Returnerer null hvis dokumentListe er null") {
                val dokumentId = finnDokumentIdForOcr(null, loggingMetadata)

                dokumentId shouldBeEqualTo null
            }
        }

        context("finnDokumentIdForPdf fungerer som den skal") {
            test("Henter riktig dokumentId for happy-case") {
                val dokumentListe: List<Dokument> =
                    listOf(
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdArkiv",
                            "brevkode",
                            listOf(Dokumentvarianter(Variantformat.ARKIV)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdOriginal",
                            "brevkode",
                            listOf(Dokumentvarianter(Variantformat.ORIGINAL)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoId",
                            "brevkode",
                            emptyList(),
                        ),
                    )

                val dokumentId = finnDokumentIdForPdf(dokumentListe, loggingMetadata)

                dokumentId.first().dokumentInfoId shouldBeEqualTo "dokumentInfoIdArkiv"
            }

            test("Kaster feil hvis dokumentListe er tom") {
                val dokumentListe: List<Dokument> = emptyList()

                assertFailsWith<RuntimeException> {
                    finnDokumentIdForPdf(dokumentListe, loggingMetadata)
                }
            }
        }

        context("dateTimeStringTilLocalDate mapper dato riktig") {
            test("Mapper dato riktig") {
                val dateTime = "2019-09-24T11:27:23"

                val dato = dateTimeStringTilLocalDateTime(dateTime, loggingMetadata)

                dato?.year shouldBeEqualTo 2019
                dato?.month shouldBeEqualTo Month.SEPTEMBER
                dato?.dayOfMonth shouldBeEqualTo 24
                dato?.hour shouldBeEqualTo 9
                dato?.minute shouldBeEqualTo 27
                dato?.second shouldBeEqualTo 23
            }

            test("Returnerer null hvis datoen ikke er en dato") {
                val dateTime = "2019-09-"

                val dato = dateTimeStringTilLocalDateTime(dateTime, loggingMetadata)

                dato shouldBeEqualTo null
            }

            test("Returnerer null hvis datoen mangler") {
                val dato = dateTimeStringTilLocalDateTime(null, loggingMetadata)

                dato shouldBeEqualTo null
            }
        }
    })
