package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
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
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.domain.Bruker
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
object BehandlingServiceSpek : Spek({
    val sykmeldingId = "1234"
    val loggingMetadata = LoggingMeta(sykmeldingId, "123", "hendelsesId")
    val datoOpprettet = LocalDateTime.now()

    val safJournalpostClientMock = mockk<SafJournalpostClient>()
    val sykmeldingServiceMock = mockk<SykmeldingService>()
    val utenlandskSykmeldingServiceMock = mockk<UtenlandskSykmeldingService>()
    val kafkaproducerreceivedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>()
    val dokArkivClientMock = mockk<DokArkivClient>()
    val kafkaproducerPapirSmRegistering = mockk<KafkaProducer<String, PapirSmRegistering>>()
    val pdlService = mockkClass(type = PdlPersonService::class, relaxed = false)
    val oppgaveService = mockk<OppgaveService>()
    val behandlingService = BehandlingService(safJournalpostClientMock, sykmeldingServiceMock, utenlandskSykmeldingServiceMock, pdlService, oppgaveService)

    beforeEachTest {
        clearAllMocks()

        coEvery { pdlService.getPdlPerson(any(), any()) } returns PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), "fnr", "aktorid", null)
        coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
            bruker = Bruker("fnr", "FNR"),
            dokumentInfoId = null,
            jpErIkkeJournalfort = true,
            gjelderUtland = false,
            datoOpprettet = datoOpprettet,
            dokumentInfoIdPdf = ""
        )
        coEvery { sykmeldingServiceMock.behandleSykmelding(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just Runs
        coEvery { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any()) } just Runs
    }

    describe("BehandlingService ende-til-ende") {
        it("Ende-til-ende journalpost med fnr") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson(eq("fnr"), any()) }
            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), any(), null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any()) }
        }

        it("Ende-til-ende journalpost med fnr og endret-tema") {
            val journalfoeringEvent = lagJournalfoeringEvent("TemaEndret", "SYM", "SKAN_IM")

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson(eq("fnr"), any()) }
            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), any(), null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any()) }
        }

        it("Ende-til-ende journalpost med fnr ny skanningleverandør") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_IM")

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson(eq("fnr"), any()) }
            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), any(), null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any()) }
        }

        it("Ende-til-ende journalpost med aktorId") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("aktorId", "AKTOERID"),
                dokumentInfoId = null,
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson("aktorId", any()) }
            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), any(), null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any()) }
        }

        it("Ende-til-ende journalpost med fnr for utlandssykmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("fnr", "FNR"),
                dokumentInfoId = null,
                jpErIkkeJournalfort = true,
                gjelderUtland = true,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson(eq("fnr"), any()) }
            coVerify(exactly = 0) { sykmeldingServiceMock.behandleSykmelding(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(eq("123"), any(), any(), any()) }
        }

        it("Ende-til-ende journalpost med aktørid for utlandssykmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("aktorId", "AKTOERID"),
                dokumentInfoId = null,
                jpErIkkeJournalfort = true,
                gjelderUtland = true,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { pdlService.getPdlPerson(eq("aktorId"), any()) }
            coVerify(exactly = 0) { sykmeldingServiceMock.behandleSykmelding(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(eq("123"), any(), any(), any()) }
        }

        it("Kaster feil hvis journalpost mangler") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns null

            assertFailsWith<TrackableException> {
                runBlocking {
                    behandlingService.handleJournalpost(
                        journalfoeringEvent, loggingMetadata, sykmeldingId,
                        "topic", kafkaproducerreceivedSykmelding,
                        dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                    )
                }
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { listOf(pdlService, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Sender null som fnr og aktørid hvis journalpost mangler brukerid") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker(null, "type"),
                dokumentInfoId = null,
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), null, null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { listOf(pdlService, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Sender null som fnr og aktørid hvis journalpost mangler brukertype") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("id", null),
                dokumentInfoId = null,
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), null, null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { listOf(pdlService, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Sender aktørid==null hvis ikke kan hente aktørid fra aktørregister") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { pdlService.getPdlPerson(any(), any()) } returns null

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), null, null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock wasNot Called }
        }

        it("Sender fnr==null hvis ikke kan hente fnr fra PDL") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("aktorId", "AKTOERID"),
                dokumentInfoId = null,
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )
            val pasient = PdlPerson(Navn("fornavn", "mellomnavn", "etternavn"), null, "aktorId", adressebeskyttelse = null)
            coEvery { pdlService.getPdlPerson(any(), any()) } returns pasient

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), pasient, null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock wasNot Called }
        }

        it("Feiler uten å opprette oppgave hvis PDL svarer med feilmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("aktorId", "AKTOERID"),
                dokumentInfoId = null,
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )
            coEvery { pdlService.getPdlPerson(any(), any()) } throws IllegalStateException("feilmelding")

            assertFailsWith<TrackableException> {
                runBlocking {
                    behandlingService.handleJournalpost(
                        journalfoeringEvent, loggingMetadata, sykmeldingId,
                        "topic", kafkaproducerreceivedSykmelding,
                        dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                    )
                }
            }

            coVerify { listOf(sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Behandler ikke melding hvis journalpost allerede er journalført") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("aktorId", "AKTOERID"),
                dokumentInfoId = null,
                jpErIkkeJournalfort = false,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet,
                dokumentInfoIdPdf = ""
            )

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEvent, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { listOf(pdlService, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Behandler ikke meldinger med feil tema") {
            val journalfoeringEventFeilTema = lagJournalfoeringEvent("MidlertidigJournalført", "FEIL_TEMA", "SKAN_NETS")

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEventFeilTema, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { listOf(safJournalpostClientMock, pdlService, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Behandler ikke meldinger med feil mottakskanal") {
            val journalfoeringEventFeilKanal = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "FEIL_KANAL")

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEventFeilKanal, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { listOf(safJournalpostClientMock, pdlService, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Behandler ikke meldinger med feil hendelsestype") {
            val journalfoeringEventFeilType = lagJournalfoeringEvent("Ferdigstilt", "SYM", "SKAN_NETS")

            runBlocking {
                behandlingService.handleJournalpost(
                    journalfoeringEventFeilType, loggingMetadata, sykmeldingId,
                    "topic", kafkaproducerreceivedSykmelding,
                    dokArkivClientMock, kafkaproducerPapirSmRegistering, "topic"
                )
            }

            coVerify { listOf(safJournalpostClientMock, pdlService, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }
    }
})

private fun lagJournalfoeringEvent(hendelsestype: String, tema: String, mottakskanal: String): JournalfoeringHendelseRecord =
    JournalfoeringHendelseRecord("hendelsesId", 1, hendelsestype, 123L, "M", "gammelt", tema, mottakskanal, "kanalreferanse", "behandlingstema")
