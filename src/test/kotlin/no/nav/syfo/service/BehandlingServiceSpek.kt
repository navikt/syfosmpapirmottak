package no.nav.syfo.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.Called
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.domain.Bruker
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.utland.UtenlandskSykmeldingService
import org.amshove.kluent.internal.assertFailsWith
import java.time.LocalDateTime

class BehandlingServiceSpek : FunSpec({
    val sykmeldingId = "1234"
    val loggingMetadata = LoggingMeta(sykmeldingId, "123", "hendelsesId")
    val datoOpprettet = LocalDateTime.now()

    val safJournalpostClientMock = mockk<SafJournalpostClient>()
    val sykmeldingServiceMock = mockk<SykmeldingService>()
    val utenlandskSykmeldingServiceMock = mockk<UtenlandskSykmeldingService>()
    val pdlService = mockkClass(type = PdlPersonService::class, relaxed = false)
    val behandlingService = BehandlingService(safJournalpostClientMock, sykmeldingServiceMock, utenlandskSykmeldingServiceMock, pdlService)

    beforeTest {
        clearAllMocks()

        coEvery { pdlService.getPdlPerson(any(), any()) } returns PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), "fnr", "aktorid", null)
        coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
            bruker = Bruker("fnr", "FNR"),
            dokumentInfoId = null,
            dokumenter = emptyList(),
            jpErIkkeJournalfort = true,
            gjelderUtland = false,
            datoOpprettet = datoOpprettet,
            dokumentInfoIdPdf = ""
        )
        coEvery { sykmeldingServiceMock.behandleSykmelding(any(), any(), any(), any(), any(), any(), any(), any()) } just Runs
        coEvery { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any(), any(), any()) } just Runs
    }

    context("BehandlingService ende-til-ende") {
        test("Ende-til-ende journalpost med fnr") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson(eq("fnr"), any()) }
            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), any(), null, datoOpprettet, any(), any(), any(), any()) }
            coVerify(exactly = 0) { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any(), any(), any()) }
        }

        test("Ende-til-ende journalpost med fnr og endret-tema") {
            val journalfoeringEvent = lagJournalfoeringEvent("TemaEndret", "SYM", "SKAN_IM")

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson(eq("fnr"), any()) }
            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), any(), null, datoOpprettet, any(), any(), any(), any()) }
            coVerify(exactly = 0) { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any(), any(), any()) }
        }

        test("Ende-til-ende journalpost med fnr ny skanningleverandør") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_IM")

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson(eq("fnr"), any()) }
            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), any(), null, datoOpprettet, any(), any(), any(), any()) }
            coVerify(exactly = 0) { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any(), any(), any()) }
        }

        test("Ende-til-ende journalpost med aktorId") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("aktorId", "AKTOERID"),
                dokumentInfoId = null,
                dokumenter = emptyList(),
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson("aktorId", any()) }
            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), any(), null, datoOpprettet, any(), any(), any(), any()) }
            coVerify(exactly = 0) { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any(), any(), any()) }
        }

        test("Ende-til-ende journalpost med fnr for utlandssykmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("fnr", "FNR"),
                dokumentInfoId = null,
                dokumenter = emptyList(),
                jpErIkkeJournalfort = true,
                gjelderUtland = true,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson(eq("fnr"), any()) }
            coVerify(exactly = 0) { sykmeldingServiceMock.behandleSykmelding(any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(eq("123"), any(), any(), any(), any(), any()) }
        }

        test("Ende-til-ende journalpost med aktørid for utlandssykmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("aktorId", "AKTOERID"),
                dokumentInfoId = null,
                dokumenter = emptyList(),
                jpErIkkeJournalfort = true,
                gjelderUtland = true,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson(eq("aktorId"), any()) }
            coVerify(exactly = 0) { sykmeldingServiceMock.behandleSykmelding(any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(eq("123"), any(), any(), any(), any(), any()) }
        }

        test("Kaster feil hvis journalpost mangler") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns null

            assertFailsWith<TrackableException> {
                runBlocking {
                    behandlingService.handleJournalpost(
                        journalfoeringEvent, loggingMetadata, sykmeldingId
                    )
                }
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { listOf(pdlService, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        test("Sender null som fnr og aktørid hvis journalpost mangler brukerid") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker(null, "type"),
                dokumentInfoId = null,
                dokumenter = emptyList(),
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), null, null, datoOpprettet, any(), any(), any(), any()) }
            coVerify { listOf(pdlService, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        test("Sender null som fnr og aktørid hvis journalpost mangler brukertype") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("id", null),
                dokumentInfoId = null,
                dokumenter = emptyList(),
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), null, null, datoOpprettet, any(), any(), any(), any()) }
            coVerify { listOf(pdlService, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        test("Sender aktørid==null hvis ikke kan hente aktørid fra aktørregister") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { pdlService.getPdlPerson(any(), any()) } returns null

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), null, null, datoOpprettet, any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock wasNot Called }
        }

        test("Sender fnr==null hvis ikke kan hente fnr fra PDL") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("aktorId", "AKTOERID"),
                dokumentInfoId = null,
                dokumenter = emptyList(),
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )
            val pasient = PdlPerson(Navn("fornavn", "mellomnavn", "etternavn"), null, "aktorId", adressebeskyttelse = null)
            coEvery { pdlService.getPdlPerson(any(), any()) } returns pasient

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), pasient, null, datoOpprettet, any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock wasNot Called }
        }

        test("Feiler uten å opprette oppgave hvis PDL svarer med feilmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("aktorId", "AKTOERID"),
                dokumentInfoId = null,
                dokumenter = emptyList(),
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )
            coEvery { pdlService.getPdlPerson(any(), any()) } throws IllegalStateException("feilmelding")

            assertFailsWith<TrackableException> {
                runBlocking {
                    behandlingService.handleJournalpost(
                        journalfoeringEvent, loggingMetadata, sykmeldingId
                    )
                }
            }

            coVerify { listOf(sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        test("Behandler ikke melding hvis journalpost allerede er journalført") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("aktorId", "AKTOERID"),
                dokumentInfoId = null,
                dokumenter = emptyList(),
                jpErIkkeJournalfort = false,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            behandlingService.handleJournalpost(
                journalfoeringEvent, loggingMetadata, sykmeldingId
            )

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { listOf(pdlService, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        test("Behandler ikke meldinger med feil tema") {
            val journalfoeringEventFeilTema = lagJournalfoeringEvent("MidlertidigJournalført", "FEIL_TEMA", "SKAN_NETS")

            behandlingService.handleJournalpost(
                journalfoeringEventFeilTema, loggingMetadata, sykmeldingId
            )

            coVerify { listOf(safJournalpostClientMock, pdlService, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        test("Behandler ikke meldinger med feil mottakskanal") {
            val journalfoeringEventFeilKanal = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "FEIL_KANAL")

            behandlingService.handleJournalpost(
                journalfoeringEventFeilKanal, loggingMetadata, sykmeldingId
            )

            coVerify { listOf(safJournalpostClientMock, pdlService, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        test("Behandler ikke meldinger med feil hendelsestype") {
            val journalfoeringEventFeilType = lagJournalfoeringEvent("Ferdigstilt", "SYM", "SKAN_NETS")

            behandlingService.handleJournalpost(
                journalfoeringEventFeilType, loggingMetadata, sykmeldingId
            )

            coVerify { listOf(safJournalpostClientMock, pdlService, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }
    }
})

private fun lagJournalfoeringEvent(hendelsestype: String, tema: String, mottakskanal: String): JournalfoeringHendelseRecord =
    JournalfoeringHendelseRecord("hendelsesId", 1, hendelsestype, 123L, "M", "gammelt", tema, mottakskanal, "kanalreferanse", "behandlingstema")
