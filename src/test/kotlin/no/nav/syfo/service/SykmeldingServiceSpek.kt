package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykSkanningMeta.AktivitetIkkeMuligType
import no.nav.helse.sykSkanningMeta.AktivitetType
import no.nav.helse.sykSkanningMeta.BehandlerType
import no.nav.helse.sykSkanningMeta.HovedDiagnoseType
import no.nav.helse.sykSkanningMeta.MedisinskVurderingType
import no.nav.helse.sykSkanningMeta.PasientType
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
object SykmeldingServiceSpek : Spek ({
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

    val sykmeldingService = SykmeldingService(sakClientMock, oppgaveserviceMock, safDokumentClientMock, norskHelsenettClientMock, aktoerIdClientMock, regelClientMock)

    beforeEachTest {
        clearAllMocks()

        coEvery { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any(), any()) } returns OppgaveResultat(1000, false)
        coEvery { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) } returns OppgaveResultat(2000, false)
        coEvery { sakClientMock.finnEllerOpprettSak(any(), any(), any()) } returns "sakId"
        coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns null
        coEvery { norskHelsenettClientMock.finnBehandler(any(), any()) } returns Behandler(emptyList(), fnrLege, "Fornavn", "Mellomnavn", "Etternavn")
        coEvery { aktoerIdClientMock.finnAktorid(any(), any()) } returns aktorIdLege
        coEvery { regelClientMock.valider(any(), any()) } returns ValidationResult(Status.OK, emptyList())
    }

    describe("SykmeldingService ende-til-ende") {
        it("Happy-case journalpost med bruker") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient, aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, aktorId, any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(fnrPasient, aktorId, eq("sakId"), journalpostId, false, any(), any()) }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis fnr mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = null, aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(any(), any(), any(), any())!! wasNot Called }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, false, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any(), any()) wasNot Called }
        }

        it("Oppretter fordelingsoppgave hvis aktørid mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient, aktorId = null, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(any(), any(), any(), any())!! wasNot Called }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, false, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(any(), any(), any()) wasNot Called }
            coVerify { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any(), any(), any()) wasNot Called }
        }

        it("Går videre selv om henting av dokument feiler") {
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } throws RuntimeException("Noe gikk galt")

            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient, aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, aktorId, any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(fnrPasient, aktorId, eq("sakId"), journalpostId, false, any(), any()) }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) wasNot Called }
        }

        it("Henter ikke dokument hvis dokumentInfoId mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient, aktorId = aktorId, dokumentInfoId = null, datoOpprettet = datoOpprettet, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, any(), any(), any())!! wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, aktorId, any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(fnrPasient, aktorId, eq("sakId"), journalpostId, false, any(), any()) }
            coVerify { oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any()) wasNot Called }
        }

        it("Henter ikke dokument hvis datoOpprettet mangler") {
            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient, aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = null, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, any(), any(), any())!! wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, aktorId, any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(fnrPasient, aktorId, eq("sakId"), journalpostId, false, any(), any()) }
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
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient, aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
            coVerify { aktoerIdClientMock.finnAktorid(fnrLege, any()) }
            coVerify { regelClientMock.valider(any(), any()) }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, aktorId, any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(fnrPasient, aktorId, eq("sakId"), journalpostId, false, any(), any()) }
        }

        it("Går videre og oppretter oppgave selv om mapping feiler") {
            coEvery { safDokumentClientMock.hentDokument(any(), any(), any(), any()) } returns Skanningmetadata().apply {
                sykemeldinger = SykemeldingerType().apply {
                    pasient = PasientType().apply { fnr = "feilFnr" }
                    behandler = BehandlerType().apply { hpr = BigInteger("123456") }
                }
            }

            runBlocking {
                sykmeldingService.behandleSykmelding(journalpostId = journalpostId, fnr = fnrPasient, aktorId = aktorId, dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
            }

            coVerify { safDokumentClientMock.hentDokument(journalpostId, dokumentInfoId, any(), any()) }
            coVerify { norskHelsenettClientMock.finnBehandler(eq("123456"), any()) }
            coVerify { aktoerIdClientMock.finnAktorid(fnrLege, any()) }
            coVerify { regelClientMock.valider(any(), any()) wasNot Called }
            coVerify { sakClientMock.finnEllerOpprettSak(sykmeldingId, aktorId, any()) }
            coVerify { oppgaveserviceMock.opprettOppgave(fnrPasient, aktorId, eq("sakId"), journalpostId, false, any(), any()) }
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
                sykmelder = sykmeldingService.hentSykmelder(ocrFil= ocrFil, loggingMeta = loggingMetadata, sykmeldingId = sykmeldingId)
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
