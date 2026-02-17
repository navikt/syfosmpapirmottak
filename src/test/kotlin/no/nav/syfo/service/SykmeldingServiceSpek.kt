package no.nav.syfo.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.spyk
import java.io.IOException
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import no.nav.helse.papirsykemelding.AktivitetIkkeMuligType
import no.nav.helse.papirsykemelding.AktivitetType
import no.nav.helse.papirsykemelding.BehandlerType
import no.nav.helse.papirsykemelding.HovedDiagnoseType
import no.nav.helse.papirsykemelding.MedisinskVurderingType
import no.nav.helse.papirsykemelding.PasientType
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.helse.papirsykemelding.SykemeldingerType
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.Icpc2BDiagnoser
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.NyRegelClient
import no.nav.syfo.client.OppgaveResponse
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafNotFoundException
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer

class SykmeldingServiceSpek :
    FunSpec({
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
        val pdlPerson =
            PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), "fnr", "aktorId", null)
        val oppgaveserviceMock = mockk<OppgaveService>()
        val safDokumentClientMock = mockk<SafDokumentClient>()
        val norskHelsenettClientMock = mockk<NorskHelsenettClient>()
        val regelClientMock = mockk<NyRegelClient>()
        val kafkaproducerreceivedSykmeldingMock =
            mockk<KafkaProducer<String, ReceivedSykmeldingWithValidation>>(relaxed = true)
        val smtssClientMock = mockk<SmtssClient>()
        val dokArkivClientMock = mockk<DokArkivClient>()
        val kafkaproducerPapirSmRegistering =
            mockk<KafkaProducer<String, PapirSmRegistering>>(relaxed = true)
        val pdlService = mockkClass(type = PdlPersonService::class, relaxed = false)
        val icpc2BDiagnoserDeferred =
            CompletableDeferred<Map<String, List<Icpc2BDiagnoser>>>(emptyMap())
        val sykmeldingService =
            SykmeldingService(
                oppgaveserviceMock,
                safDokumentClientMock,
                norskHelsenettClientMock,
                regelClientMock,
                smtssClientMock,
                pdlService,
                "ok",
                kafkaproducerreceivedSykmeldingMock,
                dokArkivClientMock,
                kafkaproducerPapirSmRegistering,
                "smregistrering",
                icpc2BDiagnoserDeferred
            )

        beforeTest {
            clearAllMocks()

            coEvery { oppgaveserviceMock.hentOppgave(any(), any()) } returns
                OppgaveResponse(0, emptyList())
            coEvery {
                oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
            } returns Unit
            coEvery {
                dokArkivClientMock.oppdaterOgFerdigstillJournalpost(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns Unit
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns null
            coEvery { norskHelsenettClientMock.finnBehandler(any(), any()) } returns
                Behandler(emptyList(), fnrLege, "Fornavn", "Mellomnavn", "Etternavn")
            coEvery { regelClientMock.valider(any(), any()) } returns
                ValidationResult(Status.OK, emptyList())
            coEvery { smtssClientMock.findBestTssInfotrygdId(any(), any(), any(), any()) } returns
                "12341"

            coEvery { pdlService.getPdlPerson(any(), any()) } returns
                PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), fnrPasient, aktorId, null)
            coEvery { pdlService.getPdlPerson(fnrLege, any()) } returns
                PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), fnrLege, aktorIdLege, null)
        }

        context("SykmeldingService ende-til-ende") {
            test("Send til smregistrering hvis SAF svarer 500 ved henting av skanningMetadata") {
                val sykmeldingServiceSpy = spyk(sykmeldingService)
                coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } throws
                    IOException()

                sykmeldingServiceSpy.behandleSykmelding(
                    journalpostId = journalpostId,
                    pasient = pdlPerson,
                    dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId,
                    temaEndret = temaEndret,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                )

                coEvery {
                    safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any())
                }
                coVerify(exactly = 1) {
                    sykmeldingServiceSpy.manuellBehandling(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
                coVerify(exactly = 1) { kafkaproducerPapirSmRegistering.send(any()) }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(),)
                }
                coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
            }

            test("Send til smregistrering hvis dokumentInfoId/OCR-referanse mangler") {
                val sykmeldingServiceSpy = spyk(sykmeldingService)

                sykmeldingServiceSpy.behandleSykmelding(
                    journalpostId = journalpostId,
                    pasient = pdlPerson,
                    dokumentInfoId = null,
                    datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId,
                    temaEndret = temaEndret,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                )

                coVerify(exactly = 0) {
                    safDokumentClientMock.hentDokument(any(), any(), any(), any())
                }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(),)
                }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
                }
                coVerify(exactly = 1) { kafkaproducerPapirSmRegistering.send(any()) }
                coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
                coVerify(exactly = 1) {
                    sykmeldingServiceSpy.manuellBehandling(
                        any(),
                        any(),
                        any(),
                        eq(dokumentInfoId),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
            }

            test(
                "Send til smregistrering hvis SAF returnerer 404 ved henting av skanningMetadata"
            ) {
                coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } throws
                    SafNotFoundException("Fant ikke dokumentet for msgId 1234 i SAF")

                sykmeldingService.behandleSykmelding(
                    journalpostId = journalpostId,
                    pasient = pdlPerson,
                    dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId,
                    temaEndret = temaEndret,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                )

                coVerify {
                    safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any())
                }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(),)
                }
                coVerify(exactly = 0) { regelClientMock.valider(any(), any()) }
                coVerify(exactly = 1) { kafkaproducerPapirSmRegistering.send(any()) }
                coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
            }

            test(
                "Skanningmetadata mappes riktig til receivedSykmelding (Happy case, ordinær flyt uten feil)"
            ) {
                coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns
                    Skanningmetadata().apply {
                        sykemeldinger =
                            SykemeldingerType().apply {
                                pasient = PasientType().apply { fnr = "fnr" }
                                behandler =
                                    BehandlerType().apply {
                                        hpr = BigInteger("123456")
                                        aktivitet =
                                            AktivitetType().apply {
                                                aktivitetIkkeMulig =
                                                    AktivitetIkkeMuligType().apply {
                                                        periodeFOMDato =
                                                            LocalDate.now().minusDays(2)
                                                        periodeTOMDato =
                                                            LocalDate.now().plusDays(10)
                                                    }
                                            }
                                        medisinskVurdering =
                                            MedisinskVurderingType().apply {
                                                hovedDiagnose.add(
                                                    HovedDiagnoseType().apply {
                                                        diagnosekode = "S52.5"
                                                    },
                                                )
                                            }
                                    }
                            }
                    }

                sykmeldingService.behandleSykmelding(
                    journalpostId = journalpostId,
                    pasient = pdlPerson,
                    dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId,
                    temaEndret = temaEndret,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                )

                coVerify {
                    safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any())
                }
                coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
                coVerify { pdlService.getPdlPerson(fnrLege, any()) }
                coVerify { regelClientMock.valider(any(), any()) }
                coVerify(exactly = 0) { kafkaproducerPapirSmRegistering.send(any()) }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(),)
                }
                coVerify { kafkaproducerreceivedSykmeldingMock.send(any()) }
            }

            test("Går til smregistrering hvis behandlingsutfall er MANUELL") {
                coEvery { regelClientMock.valider(any(), any()) } returns
                    ValidationResult(Status.MANUAL_PROCESSING, emptyList())
                coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns
                    Skanningmetadata().apply {
                        sykemeldinger =
                            SykemeldingerType().apply {
                                pasient = PasientType().apply { fnr = "fnr" }
                                behandler =
                                    BehandlerType().apply {
                                        hpr = BigInteger("123456")
                                        aktivitet =
                                            AktivitetType().apply {
                                                aktivitetIkkeMulig =
                                                    AktivitetIkkeMuligType().apply {
                                                        periodeFOMDato =
                                                            LocalDate.now().minusDays(2)
                                                        periodeTOMDato =
                                                            LocalDate.now().plusDays(10)
                                                    }
                                            }
                                        medisinskVurdering =
                                            MedisinskVurderingType().apply {
                                                hovedDiagnose.add(
                                                    HovedDiagnoseType().apply {
                                                        diagnosekode = "S52.5"
                                                    },
                                                )
                                            }
                                    }
                            }
                    }

                sykmeldingService.behandleSykmelding(
                    journalpostId = journalpostId,
                    pasient = pdlPerson,
                    dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId,
                    temaEndret = temaEndret,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                )

                coVerify {
                    safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any())
                }
                coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
                coVerify { pdlService.getPdlPerson(fnrLege, any()) }
                coVerify { regelClientMock.valider(any(), any()) }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(),)
                }
                coVerify { kafkaproducerPapirSmRegistering.send(any()) }
                coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
            }

            test("Går til smregistrering hvis requireManuellBehandling == true") {
                coEvery { regelClientMock.valider(any(), any()) } returns
                    ValidationResult(Status.MANUAL_PROCESSING, emptyList())
                coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns
                    Skanningmetadata().apply {
                        sykemeldinger =
                            SykemeldingerType().apply {
                                pasient = PasientType().apply { fnr = "fnr" }
                                behandler =
                                    BehandlerType().apply {
                                        hpr = BigInteger("123456")
                                        aktivitet =
                                            AktivitetType().apply {
                                                aktivitetIkkeMulig =
                                                    AktivitetIkkeMuligType().apply {
                                                        periodeFOMDato =
                                                            LocalDate.now().minusDays(180)
                                                        periodeTOMDato = LocalDate.now()
                                                    }
                                            }
                                        medisinskVurdering =
                                            MedisinskVurderingType().apply {
                                                hovedDiagnose.add(
                                                    HovedDiagnoseType().apply {
                                                        diagnosekode = "S52.5"
                                                    },
                                                )
                                            }
                                    }
                            }
                    }

                sykmeldingService.behandleSykmelding(
                    journalpostId = journalpostId,
                    pasient = pdlPerson,
                    dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId,
                    temaEndret = temaEndret,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                )

                coVerify {
                    safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any())
                }
                coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
                coVerify { pdlService.getPdlPerson(fnrLege, any()) }
                coVerify { regelClientMock.valider(any(), any()) }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(),)
                }
                coVerify { kafkaproducerPapirSmRegistering.send(any()) }
                coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
            }

            test("Går til smregistrering hvis mapping feiler") {
                coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns
                    Skanningmetadata().apply {
                        sykemeldinger =
                            SykemeldingerType().apply {
                                pasient = PasientType().apply { fnr = "feilFnr" }
                                behandler = BehandlerType().apply { hpr = BigInteger("123456") }
                            }
                    }

                sykmeldingService.behandleSykmelding(
                    journalpostId = journalpostId,
                    pasient = pdlPerson,
                    dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet,
                    dokumentInfoIdPdf = dokumentInfoId,
                    temaEndret = temaEndret,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                )

                coVerify {
                    safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any())
                }
                coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
                coVerify { pdlService.getPdlPerson(fnrLege, any()) }
                coVerify(exactly = 0) { regelClientMock.valider(any(), any()) }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(),)
                }
                coVerify { kafkaproducerPapirSmRegistering.send(any()) }
                coVerify(exactly = 0) { kafkaproducerreceivedSykmeldingMock.send(any()) }
            }
        }

        context("HentSykmelder") {
            test("Happy-case") {
                val ocrFil =
                    Skanningmetadata().apply {
                        sykemeldinger =
                            SykemeldingerType().apply {
                                pasient = PasientType().apply { fnr = "fnr" }
                                behandler = BehandlerType().apply { hpr = BigInteger("123456") }
                            }
                    }
                val sykmelder =
                    sykmeldingService.hentSykmelder(
                        ocrFil = ocrFil,
                        loggingMeta = loggingMetadata,
                        sykmeldingId = sykmeldingId
                    )

                sykmelder.hprNummer shouldBeEqualTo "123456"
                sykmelder.aktorId shouldBeEqualTo aktorIdLege
                sykmelder.fnr shouldBeEqualTo fnrLege
                sykmelder.fornavn shouldBeEqualTo "Fornavn"
                sykmelder.mellomnavn shouldBeEqualTo "Mellomnavn"
                sykmelder.etternavn shouldBeEqualTo "Etternavn"
            }

            test("Feiler hvis man ikke finner behandler i HPR") {
                coEvery { norskHelsenettClientMock.finnBehandler(any(), any()) } returns null
                val ocrFil =
                    Skanningmetadata().apply {
                        sykemeldinger =
                            SykemeldingerType().apply {
                                pasient = PasientType().apply { fnr = "fnr" }
                                behandler = BehandlerType().apply { hpr = BigInteger("123456") }
                            }
                    }

                assertFailsWith<IllegalStateException> {
                    runBlocking {
                        sykmeldingService.hentSykmelder(
                            ocrFil = ocrFil,
                            loggingMeta = loggingMetadata,
                            sykmeldingId = sykmeldingId
                        )
                    }
                }
            }

            test("Feiler hvis man ikke finner aktørid for behandler") {
                coEvery { pdlService.getPdlPerson(any(), any()) } returns null
                val ocrFil =
                    Skanningmetadata().apply {
                        sykemeldinger =
                            SykemeldingerType().apply {
                                pasient = PasientType().apply { fnr = "fnr" }
                                behandler = BehandlerType().apply { hpr = BigInteger("123456") }
                            }
                    }

                assertFailsWith<IllegalStateException> {
                    runBlocking {
                        sykmeldingService.hentSykmelder(
                            ocrFil = ocrFil,
                            loggingMeta = loggingMetadata,
                            sykmeldingId = sykmeldingId
                        )
                    }
                }
            }
        }
    })
