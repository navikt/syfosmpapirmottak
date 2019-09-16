package no.nav.syfo.client

import FindJournalpostQuery
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import type.Variantformat

@KtorExperimentalAPI
object SafJournalpostClientSpek : Spek({
    val loggingMetadata = LoggingMeta("sykmeldingId","123", "hendelsesId")

    describe("finnDokumentIdForOcr fungerer som den skal") {
        it("Henter riktig dokumentId for happy-case") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = listOf(
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoIdArkiv", "brevkode",
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ARKIV))),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoIdOriginal", "brevkode",
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ORIGINAL))),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoId", "brevkode", emptyList()))

            val dokumentId = finnDokumentIdForOcr(dokumentListe, loggingMetadata)

            dokumentId shouldEqual "dokumentInfoIdOriginal"
        }

        it("Returnerer null hvis dokumentListe er tom") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = emptyList()

            val dokumentId = finnDokumentIdForOcr(dokumentListe, loggingMetadata)

            dokumentId shouldEqual null
        }

        it("Returnerer null hvis dokumentListe er null") {
            val dokumentId = finnDokumentIdForOcr(null, loggingMetadata)

            dokumentId shouldEqual null
        }
    }

    describe("sykmeldingGjelderUtland gir riktig resultat") {
        it("Returnerer true hvis brevkoden er utland") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = listOf(
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoIdArkiv", BREVKODE_UTLAND,
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ARKIV))),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoIdOriginal", BREVKODE_UTLAND,
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ORIGINAL))),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoId", BREVKODE_UTLAND, emptyList()),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "annenDokumentInfoId", "brevkode", emptyList()))

            val utenlandskSykmelding = sykmeldingGjelderUtland(dokumentListe, "dokumentInfoIdOriginal", loggingMetadata)

            utenlandskSykmelding shouldEqual true
        }

        it("Returnerer false hvis brevkoden ikke er utland") {
            val dokumentListe: List<FindJournalpostQuery.Dokumenter> = listOf(
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoIdArkiv", BREVKODE_UTLAND,
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ARKIV))),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoIdOriginal", "NAV-skjema",
                    listOf(FindJournalpostQuery.Dokumentvarianter("dokumentvariant", Variantformat.ORIGINAL))),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "dokumentInfoId", "annen brevkode", emptyList()),
                FindJournalpostQuery.Dokumenter("dokumentinfo", "annenDokumentInfoId", "brevkode", emptyList()))

            val utenlandskSykmelding = sykmeldingGjelderUtland(dokumentListe, "dokumentInfoIdOriginal", loggingMetadata)

            utenlandskSykmelding shouldEqual false
        }

        it("Returnerer true hvis dokumentId mangler") {
            val utenlandskSykmelding = sykmeldingGjelderUtland(null, null, loggingMetadata)

            utenlandskSykmelding shouldEqual true
        }
    }
})
