package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.Called
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import java.time.LocalDateTime
import javax.jms.MessageProducer
import javax.jms.Session
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.domain.Bruker
import no.nav.syfo.domain.JournalpostMetadata
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object BehandlingServiceSpek : Spek({
    val sykmeldingId = "1234"
    val loggingMetadata = LoggingMeta(sykmeldingId, "123", "hendelsesId")
    val datoOpprettet = LocalDateTime.now()

    val aktoerIdClientMock = mockk<AktoerIdClient>()
    val safJournalpostClientMock = mockk<SafJournalpostClient>()
    val sykmeldingServiceMock = mockk<SykmeldingService>()
    val utenlandskSykmeldingServiceMock = mockk<UtenlandskSykmeldingService>()
    val syfoserviceProducerMock = mockk<MessageProducer>()
    val sessionMock = mockk<Session>()
    val kafkaproducerreceivedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>()
    val kuhrSarClientMock = mockk<SarClient>()
    val dokArkivClientMock = mockk<DokArkivClient>()
    val kafkaValidationResultProducerMock = mockk<KafkaProducer<String, ValidationResult>>()
    val kafkaManuelTaskProducerMock = mockk<KafkaProducer<String, ProduceTask>>()

    val behandlingService = BehandlingService(safJournalpostClientMock, aktoerIdClientMock, sykmeldingServiceMock, utenlandskSykmeldingServiceMock)

    beforeEachTest {
        clearAllMocks()

        coEvery { aktoerIdClientMock.finnAktorid(any(), any()) } returns "aktorId"
        coEvery { aktoerIdClientMock.finnFnr(any(), any()) } returns "fnr"
        coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                bruker = Bruker("fnr", "FNR"),
                dokumentInfoId = null,
                jpErIkkeJournalfort = true,
                gjelderUtland = false,
                datoOpprettet = datoOpprettet)
        coEvery { sykmeldingServiceMock.behandleSykmelding(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just Runs
        coEvery { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any(), any()) } just Runs
    }

    describe("BehandlingService ende-til-ende") {
        it("Ende-til-ende journalpost med fnr") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                        syfoserviceProducerMock, sessionMock, "topic",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2"
                )
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { aktoerIdClientMock.finnAktorid(eq("fnr"), sykmeldingId) }
            coVerify { aktoerIdClientMock.finnFnr(any(), any())!! wasNot Called }
            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), eq("fnr"), eq("aktorId"), null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any(), any()) }
        }

        it("Ende-til-ende journalpost med aktorId") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                    bruker = Bruker("aktorId", "AKTOERID"),
                    dokumentInfoId = null,
                    jpErIkkeJournalfort = true,
                    gjelderUtland = false,
                    datoOpprettet = datoOpprettet)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                        syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { aktoerIdClientMock.finnFnr(eq("aktorId"), sykmeldingId) }
            coVerify { aktoerIdClientMock.finnAktorid(any(), any())!! wasNot Called }
            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), eq("fnr"), eq("aktorId"), null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(any(), any(), any(), any(), any()) }
        }

        it("Ende-til-ende journalpost med fnr for utlandssykmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                    bruker = Bruker("fnr", "FNR"),
                    dokumentInfoId = null,
                    jpErIkkeJournalfort = true,
                    gjelderUtland = true,
                    datoOpprettet = datoOpprettet)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                        syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { aktoerIdClientMock.finnAktorid(eq("fnr"), sykmeldingId) }
            coVerify { aktoerIdClientMock.finnFnr(any(), any())!! wasNot Called }
            coVerify(exactly = 0) { sykmeldingServiceMock.behandleSykmelding(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(eq("123"), eq("fnr"), eq("aktorId"), any(), any()) }
        }

        it("Ende-til-ende journalpost med aktørid for utlandssykmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                    bruker = Bruker("aktorId", "AKTOERID"),
                    dokumentInfoId = null,
                    jpErIkkeJournalfort = true,
                    gjelderUtland = true,
                    datoOpprettet = datoOpprettet)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                        syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { aktoerIdClientMock.finnFnr(eq("aktorId"), sykmeldingId) }
            coVerify { aktoerIdClientMock.finnAktorid(any(), any())!! wasNot Called }
            coVerify(exactly = 0) { sykmeldingServiceMock.behandleSykmelding(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock.behandleUtenlandskSykmelding(eq("123"), eq("fnr"), eq("aktorId"), any(), any()) }
        }

        it("Kaster feil hvis journalpost mangler") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns null

            assertFailsWith<TrackableException> {
                runBlocking {
                    behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                            syfoserviceProducerMock, sessionMock, "",
                            kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                            kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                            "topic1", "topic2")
                }
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { listOf(aktoerIdClientMock, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Sender null som fnr og aktørid hvis journalpost mangler brukerid") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                    bruker = Bruker(null, "type"),
                    dokumentInfoId = null,
                    jpErIkkeJournalfort = true,
                    gjelderUtland = false,
                    datoOpprettet = datoOpprettet)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                        syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), null, null, null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { listOf(aktoerIdClientMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Sender null som fnr og aktørid hvis journalpost mangler brukertype") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                    bruker = Bruker("id", null),
                    dokumentInfoId = null,
                    jpErIkkeJournalfort = true,
                    gjelderUtland = false,
                    datoOpprettet = datoOpprettet)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                        syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), null, null, null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { listOf(aktoerIdClientMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Sender aktørid==null hvis ikke kan hente aktørid fra aktørregister") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { aktoerIdClientMock.finnAktorid(any(), any()) } returns null

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                        syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), "fnr", null, null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock wasNot Called }
        }

        it("Sender fnr==null hvis ikke kan hente fnr fra aktørregister") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                    bruker = Bruker("aktorId", "AKTOERID"),
                    dokumentInfoId = null,
                    jpErIkkeJournalfort = true,
                    gjelderUtland = false,
                    datoOpprettet = datoOpprettet)
            coEvery { aktoerIdClientMock.finnFnr(any(), any()) } returns null

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                        syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { sykmeldingServiceMock.behandleSykmelding(eq("123"), null, "aktorId", null, datoOpprettet, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { utenlandskSykmeldingServiceMock wasNot Called }
        }

        it("Feiler uten å opprette oppgave hvis aktørregister svarer med feilmelding") {
            val journalfoeringEvent = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "SKAN_NETS")
            coEvery { safJournalpostClientMock.getJournalpostMetadata(any(), any()) } returns JournalpostMetadata(
                    bruker = Bruker("aktorId", "AKTOERID"),
                    dokumentInfoId = null,
                    jpErIkkeJournalfort = true,
                    gjelderUtland = false,
                    datoOpprettet = datoOpprettet)
            coEvery { aktoerIdClientMock.finnFnr(any(), any()) } throws IllegalStateException("feilmelding")

            assertFailsWith<TrackableException> {
                runBlocking {
                    behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                            syfoserviceProducerMock, sessionMock, "",
                            kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                            kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                            "topic1", "topic2")
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
                    datoOpprettet = datoOpprettet)

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEvent, loggingMetadata, sykmeldingId,
                        syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { safJournalpostClientMock.getJournalpostMetadata(eq("123"), any()) }
            coVerify { listOf(aktoerIdClientMock, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Behandler ikke meldinger med feil tema") {
            val journalfoeringEventFeilTema = lagJournalfoeringEvent("MidlertidigJournalført", "FEIL_TEMA", "SKAN_NETS")

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEventFeilTema, loggingMetadata,
                        sykmeldingId, syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { listOf(safJournalpostClientMock, aktoerIdClientMock, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Behandler ikke meldinger med feil mottakskanal") {
            val journalfoeringEventFeilKanal = lagJournalfoeringEvent("MidlertidigJournalført", "SYM", "FEIL_KANAL")

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEventFeilKanal, loggingMetadata, sykmeldingId,
                        syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding,
                        kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { listOf(safJournalpostClientMock, aktoerIdClientMock, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }

        it("Behandler ikke meldinger med feil hendelsestype") {
            val journalfoeringEventFeilType = lagJournalfoeringEvent("Ferdigstilt", "SYM", "SKAN_NETS")

            runBlocking {
                behandlingService.handleJournalpost(journalfoeringEventFeilType, loggingMetadata,
                        sykmeldingId, syfoserviceProducerMock, sessionMock, "",
                        kafkaproducerreceivedSykmelding, kuhrSarClientMock, dokArkivClientMock,
                        kafkaValidationResultProducerMock, kafkaManuelTaskProducerMock,
                        "topic1", "topic2")
            }

            coVerify { listOf(safJournalpostClientMock, aktoerIdClientMock, sykmeldingServiceMock, utenlandskSykmeldingServiceMock) wasNot Called }
        }
    }
})

private fun lagJournalfoeringEvent(hendelsestype: String, tema: String, mottakskanal: String): JournalfoeringHendelseRecord =
        JournalfoeringHendelseRecord("hendelsesId", 1, hendelsestype, 123L, "M", "gammelt", tema, mottakskanal, "kanalreferanse", "behandlingstema")
