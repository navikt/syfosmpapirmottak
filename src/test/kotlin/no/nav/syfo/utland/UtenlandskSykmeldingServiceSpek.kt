package no.nav.syfo.utland

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.client.DokumentMedTittel
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.service.BREVKODE_EGENERKLARING_UTENLANDSK_SYKMELDING
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.SYKMELDING_TYPE
import no.nav.syfo.unleash.Unleash
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
        val unleash = mockk<Unleash>()
        every { unleash.shouldOpprettOppgaveFromEgenerklaring() } returns true
        val utenlandskSykmeldingService =
            UtenlandskSykmeldingService(oppgaveserviceMock, sykDigProducer, "prod-gcp", unleash)
        val utenlandskSykmeldingServiceDev =
            UtenlandskSykmeldingService(oppgaveserviceMock, sykDigProducer, "dev-gcp", unleash)

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

            coEvery {
                oppgaveserviceMock.opprettOppgave(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns OppgaveResultat(1, false, "2990")
            coEvery {
                oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
            } returns Unit
            every { unleash.shouldOpprettOppgaveFromEgenerklaring() } returns true
        }

        context("UtenlandskSykmeldingService ende-til-ende") {
            test("Happy-case journalpost med bruker") {
                utenlandskSykmeldingService.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    dokumenter = dokumenter,
                    pasient = pasient,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    type = SYKMELDING_TYPE.UTENLANDSK
                )

                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
                }
                coVerify {
                    oppgaveserviceMock.opprettOppgave(
                        aktorId,
                        journalpostId,
                        true,
                        any(),
                        any(),
                    )
                }
                coVerify(exactly = 0) { sykDigProducer.send(any(), any(), any()) }
            }

            test("Oppretter fordelingsoppgave hvis fnr mangler") {
                val pasientCopy = pasient.copy(fnr = null)

                utenlandskSykmeldingService.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    dokumenter = dokumenter,
                    pasient = pasientCopy,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    type = SYKMELDING_TYPE.UTENLANDSK
                )

                coVerify {
                    oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, true, any(), any())
                }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
            }

            test("Oppretter fordelingsoppgave hvis aktørid mangler") {
                val pasientCopy = pasient.copy(aktorId = null)

                utenlandskSykmeldingService.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    dokumenter = emptyList(),
                    pasient = pasientCopy,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    type = SYKMELDING_TYPE.UTENLANDSK
                )

                coVerify {
                    oppgaveserviceMock.opprettFordelingsOppgave(journalpostId, true, any(), any())
                }
                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettOppgave(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        "BEH_EL_SYM",
                    )
                }
            }
            test("Sender digitaliseringsoppgave i dev-gcp") {
                utenlandskSykmeldingServiceDev.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    dokumenter = dokumenter,
                    pasient = pasient,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    type = SYKMELDING_TYPE.UTENLANDSK
                )

                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
                }
                coVerify {
                    oppgaveserviceMock.opprettOppgave(
                        aktorId,
                        journalpostId,
                        true,
                        any(),
                        any(),
                        any()
                    )
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
                                    dokumentInfoId = "321",
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
                    oppgaveserviceMock.opprettOppgave(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } returns null

                utenlandskSykmeldingServiceDev.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    dokumenter = dokumenter,
                    pasient = pasient,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    type = SYKMELDING_TYPE.UTENLANDSK
                )

                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
                }
                coVerify {
                    oppgaveserviceMock.opprettOppgave(
                        aktorId,
                        journalpostId,
                        true,
                        any(),
                        any(),
                    )
                }
                coVerify(exactly = 0) { sykDigProducer.send(any(), any(), any()) }
            }
        }

        context("UtenlandskSykmeldingService Egenerklæring med sykmelding vedlegg ende-til-ende") {
            test("Happy-case journalpost med bruker") {
                utenlandskSykmeldingService.behandleUtenlandskSykmelding(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    dokumenter = dokumenter,
                    pasient = pasient,
                    loggingMeta = loggingMetadata,
                    sykmeldingId = sykmeldingId,
                    type = SYKMELDING_TYPE.EGENERKLARING_UTLAND
                )

                coVerify(exactly = 0) {
                    oppgaveserviceMock.opprettFordelingsOppgave(any(), any(), any(), any())
                }
                coVerify {
                    oppgaveserviceMock.opprettOppgave(
                        aktorId,
                        journalpostId,
                        true,
                        any(),
                        any(),
                        "BEH_EL_SYM",
                    )
                }
                coVerify(exactly = 0) { sykDigProducer.send(any(), any(), any()) }
            }
        }
    })
