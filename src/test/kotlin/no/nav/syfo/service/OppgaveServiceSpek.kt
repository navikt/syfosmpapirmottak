package no.nav.syfo.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.util.LoggingMeta

class OppgaveServiceSpek : FunSpec({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

    val oppgaveClientMock = mockk<OppgaveClient>()

    val oppgaveService = OppgaveService(oppgaveClientMock)

    beforeTest {
        clearAllMocks()

        coEvery { oppgaveClientMock.opprettOppgave(any(), any(), any(), any(), any()) } returns OppgaveResultat(1, false)
        coEvery { oppgaveClientMock.opprettFordelingsOppgave(any(), any(), any(), any()) } returns OppgaveResultat(2, false)
    }

    context("OppgaveService ende-til-ende") {
        test("Ende-til-ende") {
            oppgaveService.opprettOppgave("aktorId", journalpostId, false, sykmeldingId, loggingMetadata)

            coVerify { oppgaveClientMock.opprettOppgave(journalpostId, eq("aktorId"), false, sykmeldingId, loggingMetadata) }
        }

        test("Ende-til-ende utland") {
            oppgaveService.opprettOppgave("aktorId", journalpostId, true, sykmeldingId, loggingMetadata)

            coVerify { oppgaveClientMock.opprettOppgave(journalpostId, eq("aktorId"), true, sykmeldingId, loggingMetadata) }
        }

        test("Ende-til-ende kode 6") {
            oppgaveService.opprettOppgave("aktorId", journalpostId, false, sykmeldingId, loggingMetadata)

            coVerify { oppgaveClientMock.opprettOppgave(journalpostId, eq("aktorId"), false, sykmeldingId, loggingMetadata) }
        }

        test("Behandlende enhet mangler") {
            oppgaveService.opprettOppgave("aktorId", journalpostId, false, sykmeldingId, loggingMetadata)

            coVerify { oppgaveClientMock.opprettOppgave(journalpostId, eq("aktorId"), false, sykmeldingId, loggingMetadata) }
        }
    }

    context("OppgaveService ende-til-ende for fordelingsoppgaver") {
        test("Ende-til-ende") {
            oppgaveService.opprettFordelingsOppgave(journalpostId, false, sykmeldingId, loggingMetadata)

            coVerify { oppgaveClientMock.opprettFordelingsOppgave(journalpostId, false, sykmeldingId, loggingMetadata) }
        }

        test("Ende-til-ende utland") {
            oppgaveService.opprettFordelingsOppgave(journalpostId, true, sykmeldingId, loggingMetadata)

            coVerify { oppgaveClientMock.opprettFordelingsOppgave(journalpostId, true, sykmeldingId, loggingMetadata) }
        }

        test("Behandlende enhet mangler") {
            oppgaveService.opprettFordelingsOppgave(journalpostId, false, sykmeldingId, loggingMetadata)

            coVerify { oppgaveClientMock.opprettFordelingsOppgave(journalpostId, false, sykmeldingId, loggingMetadata) }
        }
    }
})
