package no.nav.syfo.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.util.LoggingMeta
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object OppgaveServiceSpek : Spek({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

    val oppgaveClientMock = mockk<OppgaveClient>()

    val oppgaveService = OppgaveService(oppgaveClientMock)

    beforeEachTest {
        clearAllMocks()

        coEvery { oppgaveClientMock.opprettOppgave(any(), any(), any(), any(), any()) } returns OppgaveResultat(1, false)
        coEvery { oppgaveClientMock.opprettFordelingsOppgave(any(), any(), any(), any()) } returns OppgaveResultat(2, false)
    }

    describe("OppgaveService ende-til-ende") {
        it("Ende-til-ende") {
            runBlocking {
                oppgaveService.opprettOppgave("aktorId", journalpostId, false, sykmeldingId, loggingMetadata)
            }

            coVerify { oppgaveClientMock.opprettOppgave(journalpostId, eq("aktorId"), false, sykmeldingId, loggingMetadata) }
        }

        it("Ende-til-ende utland") {
            runBlocking {
                oppgaveService.opprettOppgave("aktorId", journalpostId, true, sykmeldingId, loggingMetadata)
            }

            coVerify { oppgaveClientMock.opprettOppgave(journalpostId, eq("aktorId"), true, sykmeldingId, loggingMetadata) }
        }

        it("Ende-til-ende kode 6") {
            runBlocking {
                oppgaveService.opprettOppgave("aktorId", journalpostId, false, sykmeldingId, loggingMetadata)
            }

            coVerify { oppgaveClientMock.opprettOppgave(journalpostId, eq("aktorId"), false, sykmeldingId, loggingMetadata) }
        }

        it("Behandlende enhet mangler") {
            runBlocking {
                oppgaveService.opprettOppgave("aktorId", journalpostId, false, sykmeldingId, loggingMetadata)
            }

            coVerify { oppgaveClientMock.opprettOppgave(journalpostId, eq("aktorId"), false, sykmeldingId, loggingMetadata) }
        }
    }

    describe("OppgaveService ende-til-ende for fordelingsoppgaver") {
        it("Ende-til-ende") {
            runBlocking {
                oppgaveService.opprettFordelingsOppgave(journalpostId, false, sykmeldingId, loggingMetadata)
            }

            coVerify { oppgaveClientMock.opprettFordelingsOppgave(journalpostId, false, sykmeldingId, loggingMetadata) }
        }

        it("Ende-til-ende utland") {
            runBlocking {
                oppgaveService.opprettFordelingsOppgave(journalpostId, true, sykmeldingId, loggingMetadata)
            }

            coVerify { oppgaveClientMock.opprettFordelingsOppgave(journalpostId, true, sykmeldingId, loggingMetadata) }
        }

        it("Behandlende enhet mangler") {
            runBlocking {
                oppgaveService.opprettFordelingsOppgave(journalpostId, false, sykmeldingId, loggingMetadata)
            }

            coVerify { oppgaveClientMock.opprettFordelingsOppgave(journalpostId, false, sykmeldingId, loggingMetadata) }
        }
    }
})
