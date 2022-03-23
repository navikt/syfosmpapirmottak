package no.nav.syfo.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.LoggingMeta
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UtenlandskSykmeldingServiceSpek : Spek({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val fnr = "fnr"
    val aktorId = "aktorId"
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

    val oppgaveserviceMock = mockk<OppgaveService>()
    val pasient = PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), fnr, aktorId, null)
    val utenlandskSykmeldingService = UtenlandskSykmeldingService(oppgaveserviceMock)

    beforeEachTest {
        clearAllMocks()

        coEvery { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any()) } returns Unit
        coEvery { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) } returns Unit
    }

    describe("UtenlandskSykmeldingService ende-til-ende") {
        it("Happy-case journalpost med bruker") {
            runBlocking {
                utenlandskSykmeldingService.behandleUtenlandskSykmelding(journalpostId = journalpostId, pasient = pasient, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify(exactly = 0) { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(aktorId, journalpostId, true, any(), any()) }
        }

        it("Oppretter fordelingsoppgave hvis fnr mangler") {
            val pasientCopy = pasient.copy(fnr = null)
            runBlocking {
                utenlandskSykmeldingService.behandleUtenlandskSykmelding(journalpostId = journalpostId, pasient = pasientCopy, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, true, any(), any()) }
            coVerify(exactly = 0) { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any()) }
        }

        it("Oppretter fordelingsoppgave hvis aktørid mangler") {
            val pasientCopy = pasient.copy(aktorId = null)
            runBlocking {
                utenlandskSykmeldingService.behandleUtenlandskSykmelding(journalpostId = journalpostId, pasient = pasientCopy, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, true, any(), any()) }
            coVerify(exactly = 0) { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any()) }
        }
    }
})
