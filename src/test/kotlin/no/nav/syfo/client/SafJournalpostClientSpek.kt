package no.nav.syfo.client

import FindJournalpostQuery
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import type.Variantformat
import java.time.Month
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
object SafJournalpostClientSpek : Spek({
    val loggingMetadata = LoggingMeta("sykmeldingId", "123", "hendelsesId")

    describe("finnDokumentIdForOcr fungerer som den skal") {
        it("Henter riktig dokumentId for happy-case") {
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

        it("Returnerer null hvis dokumentListe er tom") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = emptyList()

            val dokumentId = finnDokumentIdForOcr(dokumentListe, loggingMetadata)

            dokumentId shouldBeEqualTo null
        }

        it("Returnerer null hvis dokumentListe er null") {
            val dokumentId = finnDokumentIdForOcr(null, loggingMetadata)

            dokumentId shouldBeEqualTo null
        }
    }

    describe("finnDokumentIdForPdf fungerer som den skal") {
        it("Henter riktig dokumentId for happy-case") {
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

        it("Kaster feil hvis dokumentListe er tom") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = emptyList()

            assertFailsWith<RuntimeException> {
                finnDokumentIdForPdf(dokumentListe, loggingMetadata)
            }
        }
    }

    describe("dateTimeStringTilLocalDate mapper dato riktig") {
        it("Mapper dato riktig") {
            val dateTime = "2019-09-24T11:27:23"

            val dato = dateTimeStringTilLocalDateTime(dateTime, loggingMetadata)

            dato?.year shouldBeEqualTo 2019
            dato?.month shouldBeEqualTo Month.SEPTEMBER
            dato?.dayOfMonth shouldBeEqualTo 24
            dato?.hour shouldBeEqualTo 11
            dato?.minute shouldBeEqualTo 27
            dato?.second shouldBeEqualTo 23
        }

        it("Returnerer null hvis datoen ikke er en dato") {
            val dateTime = "2019-09-"

            val dato = dateTimeStringTilLocalDateTime(dateTime, loggingMetadata)

            dato shouldBeEqualTo null
        }

        it("Returnerer null hvis datoen mangler") {
            val dato = dateTimeStringTilLocalDateTime(null, loggingMetadata)

            dato shouldBeEqualTo null
        }
    }

    describe("sykmeldingGjelderUtland gir riktig resultat") {
        it("Returnerer true hvis brevkoden er utland") {
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

        it("Returnerer false hvis brevkoden ikke er utland") {
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

        it("Returnerer true hvis dokumentliste mangler") {
            val utenlandskSykmelding = sykmeldingGjelderUtland(null, null, loggingMetadata)

            utenlandskSykmelding shouldBeEqualTo true
        }

        it("Returnerer true hvis brevkoden er utland og OCR-dokument mangler") {
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

        it("Returnerer false hvis brevkoden ikke er utland og OCR-dokument mangler") {
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
