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
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object OppgaveServiceSpek : Spek ({
    val sykmeldingId = "1234"
    val journalpostId = "123"
    val loggingMetadata = LoggingMeta(sykmeldingId,journalpostId, "hendelsesId")

    val oppgaveClientMock = mockk<OppgaveClient>()
    val personV3Mock = mockk<PersonV3>()
    val diskresjonskodeV1Mock = mockk<DiskresjonskodePortType>()
    val arbeidsfordelingV1Mock = mockk<ArbeidsfordelingV1>()

    val oppgaveService = OppgaveService(oppgaveClientMock, personV3Mock, diskresjonskodeV1Mock, arbeidsfordelingV1Mock)

    beforeEachTest {
        clearAllMocks()

        coEvery { oppgaveClientMock.opprettOppgave(any(), any(), any(), any(), any()) } returns 1
        coEvery { personV3Mock.hentGeografiskTilknytning(any()) } returns HentGeografiskTilknytningResponse().withGeografiskTilknytning(Kommune().withGeografiskTilknytning("1122"))
        coEvery { diskresjonskodeV1Mock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse()
        coEvery { arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(any()) } returns lagFinnBehandlendeEnhetListeResponse("enhetId")
    }

    describe("OppgaveService ende-til-ende") {
        it("Ende-til-ende") {
            runBlocking {
                oppgaveService.opprettOppgave("fnr", "aktorId", "sakId", journalpostId, sykmeldingId, loggingMetadata)
            }

            coVerify { personV3Mock.hentGeografiskTilknytning(any()) }
            coVerify { diskresjonskodeV1Mock.hentDiskresjonskode(coMatch { it.ident == "fnr" }) }
            coVerify {
                arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(coMatch {
                    it.arbeidsfordelingKriterier.geografiskTilknytning.value == "1122" && it.arbeidsfordelingKriterier.tema.value == "SYM" &&
                        it.arbeidsfordelingKriterier.oppgavetype.value == "JFR" && it.arbeidsfordelingKriterier.diskresjonskode == null
                })
            }
            coVerify { oppgaveClientMock.opprettOppgave(eq("sakId"), journalpostId, eq("enhetId"), eq("aktorId"), sykmeldingId) }
        }

        it("Ende-til-ende kode 6") {
            coEvery { personV3Mock.hentGeografiskTilknytning(any()) } returns HentGeografiskTilknytningResponse()
            coEvery { diskresjonskodeV1Mock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse().withDiskresjonskode("6")
            coEvery { arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(any()) } returns lagFinnBehandlendeEnhetListeResponse("2103")
            runBlocking {
                oppgaveService.opprettOppgave("fnr", "aktorId", "sakId", journalpostId, sykmeldingId, loggingMetadata)
            }

            coVerify { personV3Mock.hentGeografiskTilknytning(any()) }
            coVerify { diskresjonskodeV1Mock.hentDiskresjonskode(coMatch { it.ident == "fnr" }) }
            coVerify {
                arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(coMatch {
                    it.arbeidsfordelingKriterier.geografiskTilknytning == null && it.arbeidsfordelingKriterier.tema.value == "SYM" &&
                        it.arbeidsfordelingKriterier.oppgavetype.value == "JFR" && it.arbeidsfordelingKriterier.diskresjonskode.value == "SPSF"
                })
            }
            coVerify { oppgaveClientMock.opprettOppgave(eq("sakId"), journalpostId, eq("2103"), eq("aktorId"), sykmeldingId) }
        }

        it("Behandlende enhet mangler") {
            coEvery { arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(any()) } returns FinnBehandlendeEnhetListeResponse()
            runBlocking {
                oppgaveService.opprettOppgave("fnr", "aktorId", "sakId", journalpostId, sykmeldingId, loggingMetadata)
            }

            coVerify { personV3Mock.hentGeografiskTilknytning(any()) }
            coVerify { diskresjonskodeV1Mock.hentDiskresjonskode(coMatch { it.ident == "fnr" }) }
            coVerify {
                arbeidsfordelingV1Mock.finnBehandlendeEnhetListe(coMatch {
                    it.arbeidsfordelingKriterier.geografiskTilknytning.value == "1122" && it.arbeidsfordelingKriterier.tema.value == "SYM" &&
                        it.arbeidsfordelingKriterier.oppgavetype.value == "JFR" && it.arbeidsfordelingKriterier.diskresjonskode == null
                })
            }
            coVerify { oppgaveClientMock.opprettOppgave(eq("sakId"), journalpostId, STANDARD_NAV_ENHET, eq("aktorId"), sykmeldingId) }
        }
    }

    describe("Diskresjonskode mappes riktig") {
        it("Kode 6") {
            coEvery { diskresjonskodeV1Mock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse().withDiskresjonskode("6")
            var diskresjonskode: String? = null
            runBlocking {
                diskresjonskode = oppgaveService.fetchDiskresjonsKode("fnr")
            }

            diskresjonskode shouldEqual "SPSF"
        }

        it("Kode 7") {
            coEvery { diskresjonskodeV1Mock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse().withDiskresjonskode("7")
            var diskresjonskode: String? = null
            runBlocking {
                diskresjonskode = oppgaveService.fetchDiskresjonsKode("fnr")
            }

            diskresjonskode shouldEqual "SPFO"
        }

        it("Annen diskresjonskode mappes til null") {
            coEvery { diskresjonskodeV1Mock.hentDiskresjonskode(any()) } returns WSHentDiskresjonskodeResponse().withDiskresjonskode("5")
            var diskresjonskode: String? = null
            runBlocking {
                diskresjonskode = oppgaveService.fetchDiskresjonsKode("fnr")
            }

            diskresjonskode shouldEqual null
        }
    }
})

private fun lagFinnBehandlendeEnhetListeResponse(id: String): FinnBehandlendeEnhetListeResponse =
    FinnBehandlendeEnhetListeResponse().apply {
        behandlendeEnhetListe.add(Organisasjonsenhet().apply {
            enhetId = id
        })
    }
