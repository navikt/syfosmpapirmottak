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

        context("sykmeldingGjelderUtland gir riktig resultat") {
            test("Returnerer true hvis brevkoden er utland") {
                val dokumentListe: List<Dokument> =
                    listOf(
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdArkiv",
                            BREVKODE_UTLAND,
                            listOf(Dokumentvarianter(Variantformat.ARKIV)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdOriginal",
                            BREVKODE_UTLAND,
                            listOf(Dokumentvarianter(Variantformat.ORIGINAL)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoId",
                            BREVKODE_UTLAND,
                            emptyList(),
                        ),
                        Dokument(
                            "test-tittel",
                            "annenDokumentInfoId",
                            "brevkode",
                            emptyList(),
                        ),
                    )

                val utenlandskSykmelding =
                    sykmeldingGjelderUtland(
                        dokumentListe,
                        "dokumentInfoIdOriginal",
                        loggingMetadata
                    )

                utenlandskSykmelding shouldBeEqualTo true
            }

            test("Returnerer true hvis brevkoden er gammel kode for utland") {
                val dokumentListe: List<Dokument> =
                    listOf(
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdArkiv",
                            GAMMEL_BREVKODE_UTLAND,
                            listOf(Dokumentvarianter(Variantformat.ARKIV)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdOriginal",
                            GAMMEL_BREVKODE_UTLAND,
                            listOf(Dokumentvarianter(Variantformat.ORIGINAL)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoId",
                            GAMMEL_BREVKODE_UTLAND,
                            emptyList(),
                        ),
                        Dokument(
                            "test-tittel",
                            "annenDokumentInfoId",
                            "brevkode",
                            emptyList(),
                        ),
                    )

                val utenlandskSykmelding =
                    sykmeldingGjelderUtland(
                        dokumentListe,
                        "dokumentInfoIdOriginal",
                        loggingMetadata
                    )

                utenlandskSykmelding shouldBeEqualTo true
            }

            test("Returnerer false hvis brevkoden ikke er utland") {
                val dokumentListe: List<Dokument> =
                    listOf(
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdArkiv",
                            BREVKODE_UTLAND,
                            listOf(Dokumentvarianter(Variantformat.ARKIV)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdOriginal",
                            "NAV-skjema",
                            listOf(Dokumentvarianter(Variantformat.ORIGINAL)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoId",
                            "annen brevkode",
                            emptyList(),
                        ),
                        Dokument(
                            "test-tittel",
                            "annenDokumentInfoId",
                            "brevkode",
                            emptyList(),
                        ),
                    )

                val utenlandskSykmelding =
                    sykmeldingGjelderUtland(
                        dokumentListe,
                        "dokumentInfoIdOriginal",
                        loggingMetadata
                    )

                utenlandskSykmelding shouldBeEqualTo false
            }

            test("Returnerer true hvis dokumentliste mangler") {
                val utenlandskSykmelding = sykmeldingGjelderUtland(null, null, loggingMetadata)

                utenlandskSykmelding shouldBeEqualTo true
            }

            test("Returnerer true hvis brevkoden er utland og OCR-dokument mangler") {
                val dokumentListe: List<Dokument> =
                    listOf(
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdArkiv",
                            "annenBrevkode",
                            listOf(Dokumentvarianter(Variantformat.ARKIV)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoId",
                            BREVKODE_UTLAND,
                            emptyList(),
                        ),
                        Dokument(
                            "test-tittel",
                            "annenDokumentInfoId",
                            "brevkode",
                            emptyList(),
                        ),
                    )

                val utenlandskSykmelding =
                    sykmeldingGjelderUtland(dokumentListe, null, loggingMetadata)

                utenlandskSykmelding shouldBeEqualTo true
            }

            test("Returnerer false hvis brevkoden ikke er utland og OCR-dokument mangler") {
                val dokumentListe: List<Dokument> =
                    listOf(
                        Dokument(
                            "test-tittel",
                            "dokumentInfoIdArkiv",
                            "annenBrevkode",
                            listOf(Dokumentvarianter(Variantformat.ARKIV)),
                        ),
                        Dokument(
                            "test-tittel",
                            "dokumentInfoId",
                            "brevkode",
                            emptyList(),
                        ),
                        Dokument(
                            "test-tittel",
                            "annenDokumentInfoId",
                            "brevkode",
                            emptyList(),
                        ),
                    )

                val utenlandskSykmelding =
                    sykmeldingGjelderUtland(dokumentListe, null, loggingMetadata)

                utenlandskSykmelding shouldBeEqualTo false
            }
        }
    })
