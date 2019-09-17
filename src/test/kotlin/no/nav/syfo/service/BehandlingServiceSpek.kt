package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.LoggingMeta
import no.nav.syfo.TrackableException
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.domain.Bruker
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.domain.OppgaveResultat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
object BehandlingServiceSpek : Spek ({
    val sykmeldingId = "1234"
    val loggingMetadata = LoggingMeta(sykmeldingId,"123", "hendelsesId")

    val aktoerIdClientMock = mockk<AktoerIdClient>()
    val oppgaveserviceMock = mockk<OppgaveService>()
    val sakClientMock = mockk<SakClient>()
    val safJournalpostClientMock = mockk<SafJournalpostClient>()
    val fordelingsOppgaveServiceMock = mockk<FordelingsOppgaveService>()
    val safDokumentClientMock = mockk<SafDokumentClient>()

    val behandlingService = BehandlingService(safJournalpostClientMock, aktoerIdClientMock, sakClientMock, oppgaveserviceMock, fordelingsOppgaveServiceMock, safDokumentClientMock)

    beforeEachTest {
        clearAllMocks()

        coEvery { aktoerIdClientMock.finnAktorid(any(), any()) } returns "aktorId"
        coEvery { aktoerIdClientMock.finnFnr(any(), any()) } returns "fnr"
        coEvery { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any(), any()) } returns OppgaveResultat(1000, false)
        coEvery { sakClientMock.finnEllerOpprettSak(any(), any(), any()) } returns "sakId"
        coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(Bruker("fnr", "FNR"), null, jpErIkkeJournalfort = true, gjelderUtland = false)
        coEvery { fordelingsOppgaveServiceMock.handterJournalpostUtenBruker(any(), any(), any(), any()) } just Runs
        coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns null
    }

    describe("BehandlingService ende-til-ende") {
        it("Ende-til-ende journalpost med fnr") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { aktoerIdClientMock.finnAktorid(eq("fnr"), sykmeldingId) }
            coVerify { aktoerIdClientMock.finnFnr(any(), any())!! wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, eq("aktorId"), any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(eq("fnr"), eq("aktorId"), eq("sakId"), eq("123"), any(), any(), any()) }
        }

        it("Ende-til-ende journalpost med aktorId") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(Bruker("aktorId", "AKTOERID"), null, jpErIkkeJournalfort = true, gjelderUtland = false)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { aktoerIdClientMock.finnFnr(eq("aktorId"), sykmeldingId) }
            coVerify { aktoerIdClientMock.finnAktorid(any(), any())!! wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, eq("aktorId"), any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(eq("fnr"), eq("aktorId"), eq("sakId"), eq("123"), any(), any(), any()) }
        }

        it("Ende-til-ende journalpost for utlandssykmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(Bruker("fnr", "FNR"), null, jpErIkkeJournalfort = true, gjelderUtland = true)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { aktoerIdClientMock.finnAktorid(eq("fnr"), sykmeldingId) }
            coVerify { aktoerIdClientMock.finnFnr(any(), any())!! wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, eq("aktorId"), any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(eq("fnr"), eq("aktorId"), eq("sakId"), eq("123"), true, any(), any()) }
        }

        it("Kaster feil hvis journalpost mangler") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns null

            assertFailsWith<TrackableException> {
                runBlocking {
                    behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
                }
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { listOf(aktoerIdClientMock, oppgaveserviceMock, sakClientMock, fordelingsOppgaveServiceMock) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis journalpost mangler brukerid") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(Bruker(null, "type"), null, jpErIkkeJournalfort = true, gjelderUtland = false)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
            }

            coVerify { fordelingsOppgaveServiceMock.handterJournalpostUtenBruker(eq("123"), false, loggingMetadata, sykmeldingId) }
            coVerify { listOf(aktoerIdClientMock, oppgaveserviceMock, sakClientMock) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis journalpost mangler brukertype") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(Bruker("id", null), null, jpErIkkeJournalfort = true, gjelderUtland = false)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
            }

            coVerify { fordelingsOppgaveServiceMock.handterJournalpostUtenBruker(eq("123"), false, loggingMetadata, sykmeldingId) }
            coVerify { listOf(aktoerIdClientMock, oppgaveserviceMock, sakClientMock) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis ikke kan hente aktørid fra aktørregister") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { aktoerIdClientMock.finnAktorid(any(), any()) } returns null

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
            }

            coVerify { fordelingsOppgaveServiceMock.handterJournalpostUtenBruker(eq("123"), false, loggingMetadata, sykmeldingId) }
            coVerify { listOf(oppgaveserviceMock, sakClientMock) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis ikke kan hente fnr fra aktørregister") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(Bruker("aktorId", "AKTOERID"), null, jpErIkkeJournalfort = true, gjelderUtland = false)
            coEvery { aktoerIdClientMock.finnFnr(any(), any()) } returns null

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
            }

            coVerify { fordelingsOppgaveServiceMock.handterJournalpostUtenBruker(eq("123"), false, loggingMetadata, sykmeldingId) }
            coVerify { listOf(oppgaveserviceMock, sakClientMock) wasNot Called }
        }

        it("Oppretter ikke fordelingsoppgave hvis aktørregister svarer med feilmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(Bruker("aktorId", "AKTOERID"), null, jpErIkkeJournalfort = true, gjelderUtland = false)
            coEvery { aktoerIdClientMock.finnFnr(any(), any()) } throws IllegalStateException("feilmelding")

            assertFailsWith<TrackableException> {
                runBlocking {
                    behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
                }
            }

            coVerify { listOf(oppgaveserviceMock, sakClientMock, fordelingsOppgaveServiceMock) wasNot Called }
        }

        it("Behandler ikke melding hvis journalpost allerede er journalført") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(Bruker("aktorId", "AKTOERID"), null, jpErIkkeJournalfort = false, gjelderUtland = false)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId)
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { listOf(aktoerIdClientMock, oppgaveserviceMock, sakClientMock, fordelingsOppgaveServiceMock) wasNot Called }
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
