package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar
import javax.jms.MessageProducer
import javax.jms.Session
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykSkanningMeta.AktivitetIkkeMuligType
import no.nav.helse.sykSkanningMeta.AktivitetType
import no.nav.helse.sykSkanningMeta.BehandlerType
import no.nav.helse.sykSkanningMeta.HovedDiagnoseType
import no.nav.helse.sykSkanningMeta.MedisinskVurderingType
import no.nav.helse.sykSkanningMeta.PasientType
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.Samhandler
import no.nav.syfo.client.SarClient
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object SykmeldingServiceSpek : Spek({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val fnrPasient = "fnr"
    val aktorId = "aktorId"
    val fnrLege = "fnrLege"
    val aktorIdLege = "aktorIdLege"
    val dokumentInfoId = "dokumentInfoId"
    val datoOpprettet = LocalDateTime.now()
    val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

    val oppgaveserviceMock = mockk<OppgaveService>()
    val sakClientMock = mockk<SakClient>()
    val safDokumentClientMock = mockk<SafDokumentClient>()
    val norskHelsenettClientMock = mockk<NorskHelsenettClient>()
    val aktoerIdClientMock = mockk<AktoerIdClient>()
    val regelClientMock = mockk<RegelClient>()
    val syfoserviceProducerMock = mockk<MessageProducer>()
    val sessionMock = mockk<Session>()
    val kafkaproducerreceivedSykmeldingMock = mockk<KafkaProducer<String, ReceivedSykmelding>>()
    val kuhrSarClientMock = mockk<SarClient>()
    val dokArkivClientMock = mockk<DokArkivClient>()
    val kafkaValidationResultProducerMock = mockk<KafkaProducer<String, ValidationResult>>()
    val kafkaManuelTaskProducerMock = mockk<KafkaProducer<String, ProduceTask>>()
    val kafkaproducerPapirSmRegistering = mockk<KafkaProducer<String, PapirSmRegistering>>()

    val sykmeldingService = SykmeldingService(sakClientMock, oppgaveserviceMock, safDokumentClientMock, norskHelsenettClientMock, aktoerIdClientMock, regelClientMock)

    beforeEachTest {
        clearAllMocks()

        coEvery { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) } returns OppgaveResultat(1000, false)
        coEvery { oppgaveserviceMock.duplikatOppgave(any(), any(), any()) } returns false
        coEvery { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) } returns OppgaveResultat(2000, false)
        coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns null
        coEvery { norskHelsenettClientMock.finnBehandler(any(), any()) } returns Behandler(emptyList(), fnrLege, "Fornavn", "Mellomnavn", "Etternavn")
        coEvery { aktoerIdClientMock.finnAktorid(any(), any()) } returns aktorIdLege
        coEvery { regelClientMock.valider(any(), any()) } returns ValidationResult(Status.OK, emptyList())
        coEvery { kuhrSarClientMock.getSamhandler(any()) } returns listOf(Samhandler(
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
        ))
        coEvery { kafkaproducerPapirSmRegistering.send(any()) } returns null
    }

    describe("SykmeldingService ende-til-ende") {
        it("Happy-case journalpost med bruker") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient,
                        aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                        loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId,
                        syfoserviceProducer = syfoserviceProducerMock, session = sessionMock,
                        sm2013AutomaticHandlingTopic = "", kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmeldingMock,
                        kuhrSarClient = kuhrSarClientMock, dokArkivClient = dokArkivClientMock,
                        kafkaValidationResultProducer = kafkaValidationResultProducerMock,
                        kafkaManuelTaskProducer = kafkaManuelTaskProducerMock,
                        sm2013BehandlingsUtfallTopic = "topic1", sm2013ManualHandlingTopic = "topic2",
                        kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                        sm2013SmregistreringTopic = "topic3")
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis fnr mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = null,
                        aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                        loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId,
                        syfoserviceProducer = syfoserviceProducerMock, session = sessionMock,
                        sm2013AutomaticHandlingTopic = "", kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmeldingMock,
                        kuhrSarClient = kuhrSarClientMock, dokArkivClient = dokArkivClientMock,
                        kafkaValidationResultProducer = kafkaValidationResultProducerMock,
                        kafkaManuelTaskProducer = kafkaManuelTaskProducerMock,
                        sm2013BehandlingsUtfallTopic = "topic1", sm2013ManualHandlingTopic = "topic2",
                        kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                        sm2013SmregistreringTopic = "topic3")
            }

            coVerify { safDokumentClientMock.hentDokument(any(), any(), any(), any())!! wasNot Called }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, false, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis aktørid mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient,
                        aktorId = null, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                        loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId,
                        syfoserviceProducer = syfoserviceProducerMock, session = sessionMock,
                        sm2013AutomaticHandlingTopic = "", kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmeldingMock,
                        kuhrSarClient = kuhrSarClientMock, dokArkivClient = dokArkivClientMock,
                        kafkaValidationResultProducer = kafkaValidationResultProducerMock,
                        kafkaManuelTaskProducer = kafkaManuelTaskProducerMock,
                        sm2013BehandlingsUtfallTopic = "topic1", sm2013ManualHandlingTopic = "topic2",
                        kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                        sm2013SmregistreringTopic = "topic3")
            }

            coVerify { safDokumentClientMock.hentDokument(any(), any(), any(), any())!! wasNot Called }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, false, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) wasNot Called }
        }

        it("Går videre selv om henting av dokument feiler") {
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } throws RuntimeException("Noe gikk galt")

            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient,
                        aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                        loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId,
                        syfoserviceProducer = syfoserviceProducerMock, session = sessionMock,
                        sm2013AutomaticHandlingTopic = "", kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmeldingMock,
                        kuhrSarClient = kuhrSarClientMock, dokArkivClient = dokArkivClientMock,
                        kafkaValidationResultProducer = kafkaValidationResultProducerMock,
                        kafkaManuelTaskProducer = kafkaManuelTaskProducerMock,
                        sm2013BehandlingsUtfallTopic = "topic1", sm2013ManualHandlingTopic = "topic2",
                        kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                        sm2013SmregistreringTopic = "topic3")
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { kafkaproducerPapirSmRegistering.send(any()) }
        }

        it("Henter ikke dokument hvis dokumentInfoId mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient,
                        aktorId = aktorId, dokumentInfoId = null, datoOpprettet = datoOpprettet,
                        loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId,
                        syfoserviceProducer = syfoserviceProducerMock, session = sessionMock,
                        sm2013AutomaticHandlingTopic = "", kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmeldingMock,
                        kuhrSarClient = kuhrSarClientMock, dokArkivClient = dokArkivClientMock,
                        kafkaValidationResultProducer = kafkaValidationResultProducerMock,
                        kafkaManuelTaskProducer = kafkaManuelTaskProducerMock,
                        sm2013BehandlingsUtfallTopic = "topic1", sm2013ManualHandlingTopic = "topic2",
                        kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                        sm2013SmregistreringTopic = "topic3")
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, any(), any(), any())!! wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) wasNot Called }
        }

        it("Henter ikke dokument hvis datoOpprettet mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient,
                        aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = null,
                        loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId,
                        syfoserviceProducer = syfoserviceProducerMock, session = sessionMock,
                        sm2013AutomaticHandlingTopic = "", kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmeldingMock,
                        kuhrSarClient = kuhrSarClientMock, dokArkivClient = dokArkivClientMock,
                        kafkaValidationResultProducer = kafkaValidationResultProducerMock,
                        kafkaManuelTaskProducer = kafkaManuelTaskProducerMock,
                        sm2013BehandlingsUtfallTopic = "topic1", sm2013ManualHandlingTopic = "topic2",
                        kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                        sm2013SmregistreringTopic = "topic3")
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, any(), any(), any())!! wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) wasNot Called }
        }
    }

    describe("SykmeldingService med OCR-fil") {
        it("Happy-case") {
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
                            hovedDiagnose.add(HovedDiagnoseType().apply {
                                diagnosekode = "S52.5"
                            })
                        }
                    }
                }
            }
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient,
                        aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                        loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId,
                        syfoserviceProducer = syfoserviceProducerMock, session = sessionMock,
                        sm2013AutomaticHandlingTopic = "", kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmeldingMock,
                        kuhrSarClient = kuhrSarClientMock, dokArkivClient = dokArkivClientMock,
                        kafkaValidationResultProducer = kafkaValidationResultProducerMock,
                        kafkaManuelTaskProducer = kafkaManuelTaskProducerMock,
                        sm2013BehandlingsUtfallTopic = "topic1", sm2013ManualHandlingTopic = "topic2",
                        kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                        sm2013SmregistreringTopic = "topic3")
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
            coVerify { aktoerIdClientMock.finnAktorid(fnrLege, any()) }
            coVerify { regelClientMock.valider(any(), any()) }
            coVerify { kafkaproducerPapirSmRegistering.send(any()) }
        }

        it("Går videre og oppretter oppgave selv om mapping feiler") {
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns Skanningmetadata().apply {
                sykemeldinger = SykemeldingerType().apply {
                    pasient = PasientType().apply { fnr = "feilFnr" }
                    behandler = BehandlerType().apply { hpr = BigInteger("123456") }
                }
            }

            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient,
                        aktorId = aktorId, dokumentInfoId = dokumentInfoId,
                        datoOpprettet = datoOpprettet, loggingMeta = loggingMetadata,
                        sykmeldingId = sykmeldingId, syfoserviceProducer = syfoserviceProducerMock,
                        session = sessionMock,
                        sm2013AutomaticHandlingTopic = "", kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmeldingMock,
                        kuhrSarClient = kuhrSarClientMock, dokArkivClient = dokArkivClientMock,
                        kafkaValidationResultProducer = kafkaValidationResultProducerMock,
                        kafkaManuelTaskProducer = kafkaManuelTaskProducerMock,
                        sm2013BehandlingsUtfallTopic = "topic1", sm2013ManualHandlingTopic = "topic2",
                        kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                        sm2013SmregistreringTopic = "topic3")
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
            coVerify { aktoerIdClientMock.finnAktorid(fnrLege, any()) }
            coVerify { regelClientMock.valider(any(), any()) wasNot Called }
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

            sykmelder?.hprNummer shouldEqual "123456"
            sykmelder?.aktorId shouldEqual aktorIdLege
            sykmelder?.fnr shouldEqual fnrLege
            sykmelder?.fornavn shouldEqual "Fornavn"
            sykmelder?.mellomnavn shouldEqual "Mellomnavn"
            sykmelder?.etternavn shouldEqual "Etternavn"
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
            coEvery { aktoerIdClientMock.finnAktorid(any(), any()) } returns null
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
