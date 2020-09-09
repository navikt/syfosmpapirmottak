package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.ArbeidsFordelingClient
import no.nav.syfo.client.ArbeidsfordelingResponse
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.LoggingMeta
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import no.nav.tjeneste.pip.egen.ansatt.v1.WSHentErEgenAnsattEllerIFamilieMedEgenAnsattResponse
import no.nav.tjeneste.virksomhet.person.v3.binding.HentGeografiskTilknytningPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.feil.PersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kommune
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
internal class BehandlendeEnhetServiceTest : Spek({

    val personV3 = mockk<PersonV3>()
    val arbeidsfordelingClient = mockk<ArbeidsFordelingClient>()
    val egenAnsattV1 = mockk<EgenAnsattV1>()
    val service = BehandlendeEnhetService(personV3, egenAnsattV1, arbeidsfordelingClient)
    val loggingMeta = LoggingMeta("1", "2", "3")

    afterEachTest {
        clearAllMocks()
    }

    describe("Test BehandlendeEnhetService") {
        it("Should get null value if not found") {
            every { personV3.hentGeografiskTilknytning(any()) } returns HentGeografiskTilknytningResponse().withGeografiskTilknytning(Kommune().withGeografiskTilknytning("1234"))
            every { egenAnsattV1.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(any()) } returns WSHentErEgenAnsattEllerIFamilieMedEgenAnsattResponse().withEgenAnsatt(false)
            coEvery { arbeidsfordelingClient.finnBehandlendeEnhet(any()) } returns getArbeidsfordelingResponse()

            runBlocking {
                val enhetId = service.getBehanldendeEnhet(PdlPerson(
                        navn = Navn("fornavn", "mellomnavn", "Etternavn"),
                        fnr = "01234567891",
                        aktorId = "12345678912345",
                        adressebeskyttelse = "STRENGT_FORTROLIG"
                ), loggingMeta)
                enhetId shouldEqual "0393"
            }
        }
        it("Should call personV3 then arbeidsfordelingsClient") {
            every { personV3.hentGeografiskTilknytning(any()) } returns HentGeografiskTilknytningResponse().withGeografiskTilknytning(Kommune().withGeografiskTilknytning("1234"))
            every { egenAnsattV1.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(any()) } returns WSHentErEgenAnsattEllerIFamilieMedEgenAnsattResponse().withEgenAnsatt(false)
            coEvery { arbeidsfordelingClient.finnBehandlendeEnhet(any()) } returns getArbeidsfordelingResponse()

            runBlocking {
                val enhetId = service.getBehanldendeEnhet(PdlPerson(
                        navn = Navn("fornavn", "mellomnavn", "Etternavn"),
                        fnr = "01234567891",
                        aktorId = "12345678912345",
                        adressebeskyttelse = "STRENGT_FORTROLIG"
                ), loggingMeta)
                verify(exactly = 1) { personV3.hentGeografiskTilknytning(any()) }
                verify(exactly = 1) { egenAnsattV1.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(any()) }
                coVerify(exactly = 1) { arbeidsfordelingClient.finnBehandlendeEnhet(any()) }
                enhetId shouldEqual "0393"
            }
        }
        it("Shuold throw exception when personV3 fails") {
            every { personV3.hentGeografiskTilknytning(any()) } throws RuntimeException("Error")

            runBlocking {
                kotlin.test.assertFailsWith<java.lang.RuntimeException> {
                    service.getBehanldendeEnhet(PdlPerson(
                            navn = Navn("fornavn", "mellomnavn", "Etternavn"),
                            fnr = "01234567891",
                            aktorId = "12345678912345",
                            adressebeskyttelse = "STRENGT_FORTROLIG"
                    ), loggingMeta)
                }
                verify(exactly = 1) { personV3.hentGeografiskTilknytning(any()) }
                verify(exactly = 0) { egenAnsattV1.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(any()) }
                coVerify(exactly = 0) { arbeidsfordelingClient.finnBehandlendeEnhet(any()) }
            }
        }

        it("Should return 0393 when geografiskTilknytning not found") {
            every { personV3.hentGeografiskTilknytning(any()) } throws HentGeografiskTilknytningPersonIkkeFunnet("Person ikke funnet", PersonIkkeFunnet())
            every { egenAnsattV1.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(any()) } returns WSHentErEgenAnsattEllerIFamilieMedEgenAnsattResponse().withEgenAnsatt(false)
            coEvery { arbeidsfordelingClient.finnBehandlendeEnhet(any()) } returns getArbeidsfordelingResponse()

            runBlocking {
                val enhetId = service.getBehanldendeEnhet(PdlPerson(
                        navn = Navn("fornavn", "mellomnavn", "Etternavn"),
                        fnr = "01234567891",
                        aktorId = "12345678912345",
                        adressebeskyttelse = "STRENGT_FORTROLIG"
                ), loggingMeta)
                verify(exactly = 1) { personV3.hentGeografiskTilknytning(any()) }
                verify(exactly = 1) { egenAnsattV1.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(any()) }
                coVerify(exactly = 1) { arbeidsfordelingClient.finnBehandlendeEnhet(any()) }
                enhetId shouldEqual "0393"
            }
        }
    }
})

private fun getArbeidsfordelingResponse(): List<ArbeidsfordelingResponse> {
    return listOf(ArbeidsfordelingResponse(
            "123",
            "123",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null, null, null, null, null, null, null, null
    ))
}
