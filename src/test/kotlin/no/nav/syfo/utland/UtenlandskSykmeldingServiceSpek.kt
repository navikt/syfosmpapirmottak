package no.nav.syfo.utland

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.client.DokumentMedTittel
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.service.BREVKODE_EGENERKLARING_UTENLANDSK_SYKMELDING
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.util.LoggingMeta

class UtenlandskSykmeldingServiceSpek :
    FunSpec({
        val sykmeldingId = "1234"
        val journalpostId = "123"
        val dokumentInfoId = "321"
        val fnr = "fnr"
        val aktorId = "aktorId"
        val loggingMetadata = LoggingMeta(sykmeldingId, journalpostId, "hendelsesId")

        val oppgaveserviceMock = mockk<OppgaveService>()
        val sykDigProducer = mockk<SykDigProducer>(relaxed = true)
        val pasient = PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), fnr, aktorId, null)
        val utenlandskSykmeldingService =
            UtenlandskSykmeldingService(oppgaveserviceMock, sykDigProducer, "prod-gcp")
        val utenlandskSykmeldingServiceDev =
            UtenlandskSykmeldingService(oppgaveserviceMock, sykDigProducer, "dev-gcp")

        val dokumenter: List<DokumentMedTittel> =
            listOf(
                DokumentMedTittel(
                    dokumentInfoId = dokumentInfoId,
                    tittel = "Tittel",
                    brevkode = "NAV 08-09.06",
                ),
                DokumentMedTittel(
                    dokumentInfoId = "id2",
                    tittel = "Tittel",
                    brevkode = BREVKODE_EGENERKLARING_UTENLANDSK_SYKMELDING,
                )
            )

        beforeTest {
            clearAllMocks()

            coEvery { oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any()) } returns
                OppgaveResultat(1, false, "2990")
            coEvery {
                oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
            } returns Unit
        }

        context("UtenlandskSykmeldingService ende-til-ende") {
            test("Happy-case journalpost med bruker") {
                utenlandskSykmeldingService.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    pasient = pasient,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    dokumenter = dokumenter
                )

                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
                }
                coVerify {
                    oppgaveserviceMock.opprettOppgave(aktorId, journalpostId, true, any(), any())
                }
                coVerify(exactly = 0) { sykDigProducer.send(any(), any(), any()) }
            }

            test("Oppretter fordelingsoppgave hvis fnr mangler") {
                val pasientCopy = pasient.copy(fnr = null)

                utenlandskSykmeldingService.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    pasient = pasientCopy,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    dokumenter = dokumenter
                )

                coVerify {
                    oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, true, any(), any())
                }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any())
                }
            }

            test("Oppretter fordelingsoppgave hvis aktørid mangler") {
                val pasientCopy = pasient.copy(aktorId = null)

                utenlandskSykmeldingService.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    pasient = pasientCopy,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    dokumenter = emptyList()
                )

                coVerify {
                    oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, true, any(), any())
                }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any())
                }
            }
            test("Sender digitaliseringsoppgave i dev-gcp") {
                utenlandskSykmeldingServiceDev.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    pasient = pasient,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    dokumenter = dokumenter
                )

                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
                }
                coVerify {
                    oppgaveserviceMock.opprettOppgave(aktorId, journalpostId, true, any(), any())
                }
                coVerify {
                    sykDigProducer.send(
                        sykmeldingId,
                        match {
                            it ==
                                DigitaliseringsoppgaveKafka(
                                    oppgaveId = "1",
                                    fnr = fnr,
                                    journalpostId = journalpostId,
                                    dokumentInfoId = "id2",
                                    type = "UTLAND",
                                    dokumenter =
                                        dokumenter.map {
                                            DokumentKafka(
                                                dokumentInfoId = it.dokumentInfoId,
                                                tittel = it.tittel,
                                            )
                                        }
                                )
                        },
                        any()
                    )
                }
            }
            test("Sender ikke digitaliseringsoppgave i dev-gcp for duplikat oppgave") {
                coEvery {
                    oppgaveserviceMock.opprettOppgave(any(), any(), any(), any(), any())
                } returns null

                utenlandskSykmeldingServiceDev.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    pasient = pasient,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    dokumenter = dokumenter
                )

                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
                }
                coVerify {
                    oppgaveserviceMock.opprettOppgave(aktorId, journalpostId, true, any(), any())
                }
                coVerify(exactly = 0) { sykDigProducer.send(any(), any(), any()) }
            }
        }
    })
