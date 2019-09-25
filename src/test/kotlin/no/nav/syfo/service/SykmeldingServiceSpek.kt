package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.domain.OppgaveResultat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object SykmeldingServiceSpek : Spek ({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val fnr = "fnr"
    val aktorId = "aktorId"
    val dokumentInfoId = "dokumentInfoId"
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

    val oppgaveserviceMock = mockk<OppgaveService>()
    val sakClientMock = mockk<SakClient>()
    val safDokumentClientMock = mockk<SafDokumentClient>()

    val sykmeldingService = SykmeldingService(sakClientMock, oppgaveserviceMock, safDokumentClientMock)

    beforeEachTest {
        clearAllMocks()

        coEvery { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any(), any()) } returns OppgaveResultat(1000, false)
        coEvery { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) } returns OppgaveResultat(2000, false)
        coEvery { sakClientMock.finnEllerOpprettSak(any(), any(), any()) } returns "sakId"
        coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns null
    }

    describe("SykmeldingService ende-til-ende") {
        it("Happy-case journalpost med bruker") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnr, aktorId = aktorId, dokumentInfoId = dokumentInfoId, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, aktorId, any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(fnr, aktorId, eq("sakId"), journalpostId, false, any(), any()) }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis fnr mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = null, aktorId = aktorId, dokumentInfoId = dokumentInfoId, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(any(), any(), any(), any())!! wasNot Called }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, false, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any(), any()) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis aktørid mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnr, aktorId = null, dokumentInfoId = dokumentInfoId, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(any(), any(), any(), any())!! wasNot Called }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, false, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any(), any()) wasNot Called }
        }

        it("Går videre selv om henting av dokument feiler") {
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } throws RuntimeException("Noe gikk galt")

            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnr, aktorId = aktorId, dokumentInfoId = dokumentInfoId, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, aktorId, any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(fnr, aktorId, eq("sakId"), journalpostId, false, any(), any()) }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) wasNot Called }
        }

        it("Henter ikke dokument hvis dokumentInfoId mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnr, aktorId = aktorId, dokumentInfoId = null, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, any(), any(), any())!! wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, aktorId, any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(fnr, aktorId, eq("sakId"), journalpostId, false, any(), any()) }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) wasNot Called }
        }
    }
})
