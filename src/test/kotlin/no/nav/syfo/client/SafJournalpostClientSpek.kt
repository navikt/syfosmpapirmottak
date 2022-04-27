package no.nav.syfo.client

import FindJournalpostQuery
import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import type.Variantformat
import java.time.Month

class SafJournalpostClientSpek : FunSpec({
    val loggingMetadata = LoggingMeta("sykmeldingId", "123", "hendelsesId")

    context("finnDokumentIdForOcr fungerer som den skal") {
        test("Henter riktig dokumentId for happy-case") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = listOf(
                FindJournalpostQuery.Dokumenter(
                    "dokumentinfo", "dokumentInfoIdArkiv", "brevkode",
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ARKIV))
                ),
                FindJournalpostQuery.Dokumenter(
                    "dokumentinfo", "dokumentInfoIdOriginal", "brevkode",
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ORIGINAL))
                ),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoId", "brevkode", emptyList())
            )

            val dokumentId = finnDokumentIdForOcr(dokumentListe, loggingMetadata)

            dokumentId shouldBeEqualTo "dokumentInfoIdOriginal"
        }

        test("Returnerer null hvis dokumentListe er tom") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = emptyList()

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
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = listOf(
                FindJournalpostQuery.Dokumenter(
                    "dokumentinfo", "dokumentInfoIdArkiv", "brevkode",
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ARKIV))
                ),
                FindJournalpostQuery.Dokumenter(
                    "dokumentinfo", "dokumentInfoIdOriginal", "brevkode",
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ORIGINAL))
                ),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoId", "brevkode", emptyList())
            )

            val dokumentId = finnDokumentIdForPdf(dokumentListe, loggingMetadata)

            dokumentId shouldBeEqualTo "dokumentInfoIdArkiv"
        }

        test("Kaster feil hvis dokumentListe er tom") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = emptyList()

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
            dato?.hour shouldBeEqualTo 11
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
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = listOf(
                FindJournalpostQuery.Dokumenter(
                    "dokumentinfo", "dokumentInfoIdArkiv", BREVKODE_UTLAND,
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ARKIV))
                ),
                FindJournalpostQuery.Dokumenter(
                    "dokumentinfo", "dokumentInfoIdOriginal", BREVKODE_UTLAND,
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ORIGINAL))
                ),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoId", BREVKODE_UTLAND, emptyList()),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "annenDokumentInfoId", "brevkode", emptyList())
            )

            val utenlandskSykmelding = sykmeldingGjelderUtland(dokumentListe, "dokumentInfoIdOriginal", loggingMetadata)

            utenlandskSykmelding shouldBeEqualTo true
        }

        test("Returnerer false hvis brevkoden ikke er utland") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = listOf(
                FindJournalpostQuery.Dokumenter(
                    "dokumentinfo", "dokumentInfoIdArkiv", BREVKODE_UTLAND,
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ARKIV))
                ),
                FindJournalpostQuery.Dokumenter(
                    "dokumentinfo", "dokumentInfoIdOriginal", "NAV-skjema",
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ORIGINAL))
                ),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoId", "annen brevkode", emptyList()),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "annenDokumentInfoId", "brevkode", emptyList())
            )

            val utenlandskSykmelding = sykmeldingGjelderUtland(dokumentListe, "dokumentInfoIdOriginal", loggingMetadata)

            utenlandskSykmelding shouldBeEqualTo false
        }

        test("Returnerer true hvis dokumentliste mangler") {
            val utenlandskSykmelding = sykmeldingGjelderUtland(null, null, loggingMetadata)

            utenlandskSykmelding shouldBeEqualTo true
        }

        test("Returnerer true hvis brevkoden er utland og OCR-dokument mangler") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = listOf(
                FindJournalpostQuery.Dokumenter(
                    "dokumentinfo", "dokumentInfoIdArkiv", "annenBrevkode",
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ARKIV))
                ),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoId", BREVKODE_UTLAND, emptyList()),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "annenDokumentInfoId", "brevkode", emptyList())
            )

            val utenlandskSykmelding = sykmeldingGjelderUtland(dokumentListe, null, loggingMetadata)

            utenlandskSykmelding shouldBeEqualTo true
        }

        test("Returnerer false hvis brevkoden ikke er utland og OCR-dokument mangler") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = listOf(
                FindJournalpostQuery.Dokumenter(
                    "dokumentinfo", "dokumentInfoIdArkiv", "annenBrevkode",
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ARKIV))
                ),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoId", "brevkode", emptyList()),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "annenDokumentInfoId", "brevkode", emptyList())
            )

            val utenlandskSykmelding = sykmeldingGjelderUtland(dokumentListe, null, loggingMetadata)

            utenlandskSykmelding shouldBeEqualTo false
        }
    }
})
