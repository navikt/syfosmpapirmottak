package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.LoggingMeta
import no.nav.syfo.STANDARD_NAV_ENHET
import no.nav.syfo.client.OppgaveClient
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.pip.diskresjonskode.meldinger.WSHentDiskresjonskodeResponse
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Organisasjonsenhet
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeResponse
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kommune
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object FordelingsOppgaveServiceSpek : Spek ({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val loggingMetadata = LoggingMeta(sykmeldingId,journalpostId, "hendelsesId")

    val oppgaveClientMock = mockk<OppgaveClient>()
    val personV3Mock = mockk<PersonV3>()
    val diskresjonskodeV1Mock = mockk<DiskresjonskodePortType>()
    val arbeidsfordelingV1Mock = mockk<ArbeidsfordelingV1>()
    val oppgaveService = OppgaveService(oppgaveClientMock, personV3Mock, diskresjonskodeV1Mock, arbeidsfordelingV1Mock)

    val fordelingsOppgaveService = FordelingsOppgaveService(oppgaveService)

    beforeEachTest {
        clearAllMocks()

        coEvery { oppgaveClientMock.opprettFordelingsOppgave(any(), any(), any(), any(), any()) } returns 1
        coEvery { personV3Mock.hentGeografiskTilknytning(any()) } returns HentGeografiskTilknytningResponse().withGeografiskTilknytning(Kommune().withGeografiskTilknytning("1122"))
        coEvery { diskresjonskodeV1Mock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse()
        coEvery { arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(any()) } returns FinnBehandlendeEnhetListeResponse().apply {
            behandlendeEnhetListe.add(Organisasjonsenhet().apply {
                enhetId = "enhetId"
            })
        }
    }

    describe("FordelingsOppgaveService ende-til-ende") {
        it("Ende-til-ende") {
            runBlocking {
                fordelingsOppgaveService.handterJournalpostUtenBruker(journalpostId, false, loggingMetadata, sykmeldingId)
            }

            coVerify {
                arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(coMatch {
                    it.arbeidsfordelingKriterier.geografiskTilknytning == null && it.arbeidsfordelingKriterier.tema.value == "SYM" &&
                        it.arbeidsfordelingKriterier.oppgavetype.value == "FDR" && it.arbeidsfordelingKriterier.diskresjonskode == null
                })
            }
            coVerify { oppgaveClientMock.opprettFordelingsOppgave(journalpostId, eq("enhetId"), false, sykmeldingId, loggingMetadata) }
        }

        it("Ende-til-ende utland") {
            runBlocking {
                fordelingsOppgaveService.handterJournalpostUtenBruker(journalpostId, true, loggingMetadata, sykmeldingId)
            }

            coVerify {
                arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(coMatch {
                    it.arbeidsfordelingKriterier.geografiskTilknytning == null && it.arbeidsfordelingKriterier.tema.value == "SYM" &&
                        it.arbeidsfordelingKriterier.oppgavetype.value == "FDR" && it.arbeidsfordelingKriterier.behandlingstype.value == "ae0106" && it.arbeidsfordelingKriterier.diskresjonskode == null
                })
            }
            coVerify { oppgaveClientMock.opprettFordelingsOppgave(journalpostId, eq("enhetId"), true, sykmeldingId, loggingMetadata) }
        }

        it("Behandlende enhet mangler") {
            coEvery { arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(any()) } returns FinnBehandlendeEnhetListeResponse()
            runBlocking {
                fordelingsOppgaveService.handterJournalpostUtenBruker(journalpostId, false, loggingMetadata, sykmeldingId)
            }

            coVerify {
                arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(coMatch {
                    it.arbeidsfordelingKriterier.geografiskTilknytning == null && it.arbeidsfordelingKriterier.tema.value == "SYM" &&
                        it.arbeidsfordelingKriterier.oppgavetype.value == "FDR" && it.arbeidsfordelingKriterier.diskresjonskode == null
                })
            }
            coVerify { oppgaveClientMock.opprettFordelingsOppgave(journalpostId, STANDARD_NAV_ENHET, false, sykmeldingId, loggingMetadata) }
        }
    }
})
