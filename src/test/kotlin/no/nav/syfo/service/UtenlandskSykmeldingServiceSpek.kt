package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.Called
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.SakClient
import no.nav.syfo.domain.OppgaveResultat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object UtenlandskSykmeldingServiceSpek : Spek ({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val fnr = "fnr"
    val aktorId = "aktorId"
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

    val oppgaveserviceMock = mockk<OppgaveService>()
    val sakClientMock = mockk<SakClient>()
    val fordelingsOppgaveServiceMock = mockk<FordelingsOppgaveService>()

    val utenlandskSykmeldingService = UtenlandskSykmeldingService(sakClientMock, oppgaveserviceMock, fordelingsOppgaveServiceMock)

    beforeEachTest {
        clearAllMocks()

        coEvery { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any(), any()) } returns OppgaveResultat(1000, false)
        coEvery { sakClientMock.finnEllerOpprettSak(any(), any(), any()) } returns "sakId"
        coEvery { fordelingsOppgaveServiceMock.handterJournalpostUtenBruker(any(), any(), any(), any()) } just Runs
    }

    describe("UtenlandskSykmeldingService ende-til-ende") {
        it("Happy-case journalpost med bruker") {
            runBlocking {
                utenlandskSykmeldingService.behandleUtenlandskSykmelding(journalpostId = journalpostId, fnr = fnr, aktorId = aktorId, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { fordelingsOppgaveServiceMock.handterJournalpostUtenBruker(any(), any(), any(), any()) wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, aktorId, any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(fnr, aktorId, eq("sakId"), journalpostId, true, any(), any()) }
        }

        it("Oppretter fordelingsoppgave hvis fnr mangler") {
            runBlocking {
                utenlandskSykmeldingService.behandleUtenlandskSykmelding(journalpostId = journalpostId, fnr = null, aktorId = aktorId, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { fordelingsOppgaveServiceMock.handterJournalpostUtenBruker(journalpostId, true, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any(), any()) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis aktørid mangler") {
            runBlocking {
                utenlandskSykmeldingService.behandleUtenlandskSykmelding(journalpostId = journalpostId, fnr = fnr, aktorId = null, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { fordelingsOppgaveServiceMock.handterJournalpostUtenBruker(journalpostId, true, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any(), any()) wasNot Called }
        }
    }
})
