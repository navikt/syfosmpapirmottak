package no.nav.syfo.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykSkanningMeta.AktivitetIkkeMuligType
import no.nav.helse.sykSkanningMeta.AktivitetType
import no.nav.helse.sykSkanningMeta.BehandlerType
import no.nav.helse.sykSkanningMeta.HovedDiagnoseType
import no.nav.helse.sykSkanningMeta.MedisinskVurderingType
import no.nav.helse.sykSkanningMeta.PasientType
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.OppgaveResponse
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafNotFoundException
import no.nav.syfo.client.Samhandler
import no.nav.syfo.client.SarClient
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.domain.SyfoserviceSykmeldingKafkaMessage
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar

object SykmeldingServiceSpek : Spek({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val fnrPasient = "fnr"
    val aktorId = "aktorId"
    val fnrLege = "fnrLege"
    val aktorIdLege = "aktorIdLege"
    val dokumentInfoId = "dokumentInfoId"
    val temaEndret = false
    val datoOpprettet = LocalDateTime.now()
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")
    val pdlPerson = PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), "fnr", "aktorId", null)
    val oppgaveserviceMock = mockk<OppgaveService>()
    val safDokumentClientMock = mockk<SafDokumentClient>()
    val norskHelsenettClientMock = mockk<NorskHelsenettClient>()
    val regelClientMock = mockk<RegelClient>()
    val kafkaproducerreceivedSykmeldingMock = mockk<KafkaProducer<String, ReceivedSykmelding>>(relaxed = true)
    val kuhrSarClientMock = mockk<SarClient>()
    val dokArkivClientMock = mockk<DokArkivClient>()
    val kafkaproducerPapirSmRegistering = mockk<KafkaProducer<String, PapirSmRegistering>>(relaxed = true)
    val kafkaProducerSyfoservice = mockk<KafkaProducer<String, SyfoserviceSykmeldingKafkaMessage>>(relaxed = true)
    val pdlService = mockkClass(type = PdlPersonService::class, relaxed = false)
    val sykmeldingService = SykmeldingService(
        oppgaveserviceMock,
        safDokumentClientMock,
        norskHelsenettClientMock,
        regelClientMock,
        kuhrSarClientMock,
        pdlService,
        kafkaProducerSyfoservice,
        "topic"
    )

    beforeEachTest {
        clearAllMocks()

        coEvery { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { oppgaveserviceMock.hentOppgave(any(), any()) } returns OppgaveResponse(0, emptyList())
        coEvery { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) } returns Unit
        coEvery { dokArkivClientMock.oppdaterOgFerdigstillJournalpost(any(), any(), any(), any(), any()) } returns "1"
        coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns null
        coEvery { norskHelsenettClientMock.finnBehandler(any(), any()) } returns Behandler(emptyList(), fnrLege, "Fornavn", "Mellomnavn", "Etternavn")
        coEvery { regelClientMock.valider(any(), any()) } returns ValidationResult(Status.OK, emptyList())
        coEvery { kuhrSarClientMock.getSamhandler(any()) } returns listOf(
            Samhandler(
                samh_id = "12341",
                navn = "Perhansen",
                samh_type_kode = "fALE",
                behandling_utfall_kode = "auto",
                unntatt_veiledning = "1",
                godkjent_manuell_krav = "0",
                ikke_godkjent_for_refusjon = "0",
                godkjent_egenandel_refusjon = "0",
                godkjent_for_fil = "0",
                endringslogg_tidspunkt_siste = Calendar.getInstance().time,
                samh_praksis = listOf(),
                samh_ident = listOf()
            )
        )

        coEvery { pdlService.getPdlPerson(any(), any()) } returns PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), fnrPasient, aktorId, null)
        coEvery { pdlService.getPdlPerson(fnrLege, any()) } returns PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), fnrLege, aktorIdLege, null)
    }

    describe("SykmeldingService ende-til-ende") {
        it("Send til smregistrering hvis SAF svarer 500 ved henting av skanningMetadata") {
            val sykmeldingServiceSpy = spyk(sykmeldingService)
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } throws IOException()

            runBlocking {
                sykmeldingServiceSpy.behandleSykmelding(
                    journalpostId = journalpostId,
                    pasient = pdlPerson,
                    dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId,
                    temaEndret = temaEndret,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    okSykmeldingTopic = "",
                    kafkaReceivedSykmeldingProducer = kafkaproducerreceivedSykmeldingMock,
                    dokArkivClient = dokArkivClientMock,
                    kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                    smregistreringTopic = "topic3"
                )
            }
            coEvery { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify(exactly = 1) {
                sykmeldingServiceSpy.manuellBehandling(
                    any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any()
                )
            }
            coVerify(exactly = 1) { kafkaproducerPapirSmRegistering.send(any()) }
            coVerify(exactly = 0) { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
        }

        it("Send til smregistrering hvis dokumentInfoId/OCR-referanse mangler") {

            val sykmeldingServiceSpy = spyk(sykmeldingService)

            runBlocking {
                sykmeldingServiceSpy.behandleSykmelding(
                    journalpostId = journalpostId, pasient = pdlPerson, dokumentInfoId = null, datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId, temaEndret = temaEndret, loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId, okSykmeldingTopic = "",
                    kafkaReceivedSykmeldingProducer = kafkaproducerreceivedSykmeldingMock,
                    dokArkivClient = dokArkivClientMock,
                    kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                    smregistreringTopic = "topic3"
                )
            }
            coVerify(exactly = 0) { safDokumentClientMock.hentDokument(any(), any(), any(), any()) }
            coVerify(exactly = 0) { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) }
            coVerify(exactly = 1) { kafkaproducerPapirSmRegistering.send(any()) }
            coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
            coVerify(exactly = 1) {
                sykmeldingServiceSpy.manuellBehandling(
                    any(), any(), any(), eq(dokumentInfoId), any(), any(),
                    any(), any(), any(), any(), any(), any()
                )
            }
        }

        it("Send til smregistrering hvis SAF returnerer 404 ved henting av skanningMetadata") {

            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } throws SafNotFoundException("Fant ikke dokumentet for msgId 1234 i SAF")

            runBlocking {
                sykmeldingService.behandleSykmelding(
                    journalpostId = journalpostId, pasient = pdlPerson, dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet, dokumentInfoIdPdf = dokumentInfoId, temaEndret = temaEndret,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId, okSykmeldingTopic = "",
                    kafkaReceivedSykmeldingProducer = kafkaproducerreceivedSykmeldingMock,
                    dokArkivClient = dokArkivClientMock,
                    kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                    smregistreringTopic = "topic3"
                )
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify(exactly = 0) { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { regelClientMock.valider(any(), any()) }
            coVerify(exactly = 1) { kafkaproducerPapirSmRegistering.send(any()) }
            coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
        }

        it("Skanningmetadata mappes riktig til receivedSykmelding (Happy case, ordinær flyt uten feil)") {
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns Skanningmetadata().apply {
                sykemeldinger = SykemeldingerType().apply {
                    pasient = PasientType().apply { fnr = "fnr" }
                    behandler = BehandlerType().apply {
                        hpr = BigInteger("123456")
                        aktivitet = AktivitetType().apply {
                            aktivitetIkkeMulig = AktivitetIkkeMuligType().apply {
                                periodeFOMDato = LocalDate.now().minusDays(2)
                                periodeTOMDato = LocalDate.now().plusDays(10)
                            }
                        }
                        medisinskVurdering = MedisinskVurderingType().apply {
                            hovedDiagnose.add(
                                HovedDiagnoseType().apply {
                                    diagnosekode = "S52.5"
                                }
                            )
                        }
                    }
                }
            }
            runBlocking {
                sykmeldingService.behandleSykmelding(
                    journalpostId = journalpostId, pasient = pdlPerson, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId, temaEndret = temaEndret, loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId, okSykmeldingTopic = "",
                    kafkaReceivedSykmeldingProducer = kafkaproducerreceivedSykmeldingMock,
                    dokArkivClient = dokArkivClientMock,
                    kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                    smregistreringTopic = "topic3"
                )
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
            coVerify { pdlService.getPdlPerson(fnrLege, any()) }
            coVerify { regelClientMock.valider(any(), any()) }
            coVerify(exactly = 0) { kafkaproducerPapirSmRegistering.send(any()) }
            coVerify(exactly = 0) { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) }
            coVerify { kafkaproducerreceivedSykmeldingMock.send(any()) }
        }

        it("Går til smregistrering hvis behandlingsutfall er MANUELL") {
            coEvery { regelClientMock.valider(any(), any()) } returns ValidationResult(Status.MANUAL_PROCESSING, emptyList())
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns Skanningmetadata().apply {
                sykemeldinger = SykemeldingerType().apply {
                    pasient = PasientType().apply { fnr = "fnr" }
                    behandler = BehandlerType().apply {
                        hpr = BigInteger("123456")
                        aktivitet = AktivitetType().apply {
                            aktivitetIkkeMulig = AktivitetIkkeMuligType().apply {
                                periodeFOMDato = LocalDate.now().minusDays(2)
                                periodeTOMDato = LocalDate.now().plusDays(10)
                            }
                        }
                        medisinskVurdering = MedisinskVurderingType().apply {
                            hovedDiagnose.add(
                                HovedDiagnoseType().apply {
                                    diagnosekode = "S52.5"
                                }
                            )
                        }
                    }
                }
            }
            runBlocking {
                sykmeldingService.behandleSykmelding(
                    journalpostId = journalpostId, pasient = pdlPerson, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId, temaEndret = temaEndret, loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId, okSykmeldingTopic = "",
                    kafkaReceivedSykmeldingProducer = kafkaproducerreceivedSykmeldingMock,
                    dokArkivClient = dokArkivClientMock,
                    kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                    smregistreringTopic = "topic3"
                )
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
            coVerify { pdlService.getPdlPerson(fnrLege, any()) }
            coVerify { regelClientMock.valider(any(), any()) }
            coVerify(exactly = 0) { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) }
            coVerify { kafkaproducerPapirSmRegistering.send(any()) }
            coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
        }

        it("Går til smregistrering hvis requireManuellBehandling == true") {
            coEvery { regelClientMock.valider(any(), any()) } returns ValidationResult(Status.MANUAL_PROCESSING, emptyList())
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns Skanningmetadata().apply {
                sykemeldinger = SykemeldingerType().apply {
                    pasient = PasientType().apply { fnr = "fnr" }
                    behandler = BehandlerType().apply {
                        hpr = BigInteger("123456")
                        aktivitet = AktivitetType().apply {
                            aktivitetIkkeMulig = AktivitetIkkeMuligType().apply {
                                periodeFOMDato = LocalDate.now().minusDays(180)
                                periodeTOMDato = LocalDate.now()
                            }
                        }
                        medisinskVurdering = MedisinskVurderingType().apply {
                            hovedDiagnose.add(
                                HovedDiagnoseType().apply {
                                    diagnosekode = "S52.5"
                                }
                            )
                        }
                    }
                }
            }
            runBlocking {
                sykmeldingService.behandleSykmelding(
                    journalpostId = journalpostId, pasient = pdlPerson, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId, temaEndret = temaEndret, loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId, okSykmeldingTopic = "",
                    kafkaReceivedSykmeldingProducer = kafkaproducerreceivedSykmeldingMock,
                    dokArkivClient = dokArkivClientMock,
                    kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                    smregistreringTopic = "topic3"
                )
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
            coVerify { pdlService.getPdlPerson(fnrLege, any()) }
            coVerify { regelClientMock.valider(any(), any()) }
            coVerify(exactly = 0) { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) }
            coVerify { kafkaproducerPapirSmRegistering.send(any()) }
            coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
        }

        it("Går til smregistrering hvis mapping feiler") {
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns Skanningmetadata().apply {
                sykemeldinger = SykemeldingerType().apply {
                    pasient = PasientType().apply { fnr = "feilFnr" }
                    behandler = BehandlerType().apply { hpr = BigInteger("123456") }
                }
            }

            runBlocking {
                sykmeldingService.behandleSykmelding(
                    journalpostId = journalpostId, pasient = pdlPerson, dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet, dokumentInfoIdPdf = dokumentInfoId, temaEndret = temaEndret,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId, okSykmeldingTopic = "",
                    kafkaReceivedSykmeldingProducer = kafkaproducerreceivedSykmeldingMock,
                    dokArkivClient = dokArkivClientMock,
                    kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                    smregistreringTopic = "topic3"
                )
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
            coVerify { pdlService.getPdlPerson(fnrLege, any()) }
            coVerify(exactly = 0) { regelClientMock.valider(any(), any()) }
            coVerify(exactly = 0) { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) }
            coVerify { kafkaproducerPapirSmRegistering.send(any()) }
            coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
        }
    }

    describe("HentSykmelder") {
        it("Happy-case") {
            val ocrFil = Skanningmetadata().apply {
                sykemeldinger = SykemeldingerType().apply {
                    pasient = PasientType().apply { fnr = "fnr" }
                    behandler = BehandlerType().apply { hpr = BigInteger("123456") }
                }
            }
            var sykmelder: Sykmelder? = null
            runBlocking {
                sykmelder = sykmeldingService.hentSykmelder(ocrFil = ocrFil, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            sykmelder?.hprNummer shouldBeEqualTo "123456"
            sykmelder?.aktorId shouldBeEqualTo aktorIdLege
            sykmelder?.fnr shouldBeEqualTo fnrLege
            sykmelder?.fornavn shouldBeEqualTo "Fornavn"
            sykmelder?.mellomnavn shouldBeEqualTo "Mellomnavn"
            sykmelder?.etternavn shouldBeEqualTo "Etternavn"
        }

        it("Feiler hvis man ikke finner behandler i HPR") {
            coEvery { norskHelsenettClientMock.finnBehandler(any(), any()) } returns null
            val ocrFil = Skanningmetadata().apply {
                sykemeldinger = SykemeldingerType().apply {
                    pasient = PasientType().apply { fnr = "fnr" }
                    behandler = BehandlerType().apply { hpr = BigInteger("123456") }
                }
            }

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    sykmeldingService.hentSykmelder(ocrFil = ocrFil, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
                }
            }
        }

        it("Feiler hvis man ikke finner aktørid for behandler") {
            coEvery { pdlService.getPdlPerson(any(), any()) } returns null
            val ocrFil = Skanningmetadata().apply {
                sykemeldinger = SykemeldingerType().apply {
                    pasient = PasientType().apply { fnr = "fnr" }
                    behandler = BehandlerType().apply { hpr = BigInteger("123456") }
                }
            }

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    sykmeldingService.hentSykmelder(ocrFil = ocrFil, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
                }
            }
        }
    }
})
