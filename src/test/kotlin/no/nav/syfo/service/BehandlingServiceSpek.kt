package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.LoggingMeta
import no.nav.syfo.TrackableException
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.domain.Bruker
import no.nav.syfo.domain.JournalpostMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
object BehandlingServiceSpek : Spek ({
    val sykmeldingId = "1234"
    val loggingMetadata = LoggingMeta(sykmeldingId,"123", "hendelsesId")

    val aktoerIdClientMock = mockk<AktoerIdClient>()
    coEvery { aktoerIdClientMock.finnAktorid(any(), any()) } returns "aktorId"
    coEvery { aktoerIdClientMock.finnFnr(any(), any()) } returns "fnr"

    val oppgaveserviceMock = mockk<OppgaveService>()
    coEvery { oppgaveserviceMock.createOppgave(any(), any(), any(), any(), any(), any()) } returns 1000

    val sakClientMock = mockk<SakClient>()
    coEvery{ sakClientMock.finnEllerOpprettSak(any(), any(), any()) } returns "sakId"

    val safJournalpostClientMock = mockk<SafJournalpostClient>()
    coEvery { safJournalpostClientMock.getJournalpostMetadata(any()) } returns JournalpostMetadata(Bruker("fnr", "FNR"))

    val behandlingService = BehandlingService(safJournalpostClientMock, aktoerIdClientMock, sakClientMock, oppgaveserviceMock)

    beforeEachTest {
        clearAllMocks(answers = false)
    }

    describe("BehandlingService ende-til-ende") {
        it("Ende-til-ende journalpost med fnr") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123")) }
            coVerify { aktoerIdClientMock.finnAktorid(eq("fnr"), sykmeldingId) }
            coVerify { aktoerIdClientMock.finnFnr(any(), any()) wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, eq("aktorId"), any()) }
            coVerify { oppgaveserviceMock.createOppgave(eq("fnr"), eq("aktorId"), eq("sakId"), eq("123"), any(), any()) }
        }

        it("Ende-til-ende journalpost med aktorId") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any()) } returns JournalpostMetadata(Bruker("aktorId", "AKTOERID"))

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123")) }
            coVerify { aktoerIdClientMock.finnFnr(eq("aktorId"), sykmeldingId) }
            coVerify { aktoerIdClientMock.finnAktorid(any(), any()) wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, eq("aktorId"), any()) }
            coVerify { oppgaveserviceMock.createOppgave(eq("fnr"), eq("aktorId"), eq("sakId"), eq("123"), any(), any()) }
        }

        it("Kaster feil hvis journalpost mangler bruker") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any()) } returns null

            assertFailsWith<TrackableException> {
                runBlocking {
                    behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
                }
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123")) }
            coVerify { listOf(aktoerIdClientMock, oppgaveserviceMock, sakClientMock) wasNot Called }
        }

        it("Behandler ikke meldinger med feil tema") {
            val journalfoeringEventFeilTema = lagJournalfoeringEvent("MidlertidigJournalført", "FEIL_TEMA", "SKAN_NETS")

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEventFeilTema, loggingMetadata, sykmeldingId)
            }

            coVerify { listOf(aktoerIdClientMock, oppgaveserviceMock, sakClientMock, safJournalpostClientMock) wasNot Called }
        }

        it("Behandler ikke meldinger med feil mottakskanal") {
            val journalfoeringEventFeilKanal = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "FEIL_KANAL")

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEventFeilKanal, loggingMetadata, sykmeldingId)
            }

            coVerify { listOf(aktoerIdClientMock, oppgaveserviceMock, sakClientMock, safJournalpostClientMock) wasNot Called }
        }

        it("Behandler ikke meldinger med feil hendelsestype") {
            val journalfoeringEventFeilType = lagJournalfoeringEvent("Ferdigstilt", "SYM", "SKAN_NETS")

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEventFeilType, loggingMetadata, sykmeldingId)
            }

            coVerify { listOf(aktoerIdClientMock, oppgaveserviceMock, sakClientMock, safJournalpostClientMock) wasNot Called }
        }
    }
})

private fun lagJournalfoeringEvent(hendelsestype: String, tema: String, mottakskanal: String): JournalfoeringHendelseRecord =
    JournalfoeringHendelseRecord("hendelsesId", 1, hendelsestype, 123L, "M", "gammelt", tema, mottakskanal, "kanalreferanse", "behandlingstema")
