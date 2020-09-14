package no.nav.syfo.pdl

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentIdenter
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object PdlServiceTest : Spek({

    val pdlClient = mockkClass(PdlClient::class)
    val stsOidcClient = mockkClass(StsOidcClient::class)
    val pdlService = PdlPersonService(pdlClient, stsOidcClient)

    val loggingMeta = LoggingMeta("sykmeldingId", "journalpostId", "hendelsesId")

    beforeEachTest {
        clearAllMocks()
    }

    describe("Tests PDL Service") {
        it("Hent person fra pdl uten fortrolig adresse") {
            coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse()

            runBlocking {
                val person = pdlService.getPdlPerson("01245678901", loggingMeta)
                person?.navn?.fornavn shouldEqual "fornavn"
                person?.navn?.mellomnavn shouldEqual null
                person?.navn?.etternavn shouldEqual "etternavn"
                person?.aktorId shouldEqual "987654321"
            }
        }

        it("Skal feile når person ikke finnes") {
            coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null, null), errors = null)

            runBlocking {
                val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                pdlPerson shouldBe null
            }
        }

        it("Skal feile når navn er tom liste") {
            coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(hentPerson = HentPerson(
                    navn = emptyList()
            ),
                    hentIdenter = HentIdenter(emptyList())
            ), errors = null)

            runBlocking {
                val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                pdlPerson shouldBe null
            }
        }

        it("Skal feile når navn ikke finnes") {
            coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(hentPerson = HentPerson(
                    navn = null
            ),
                    hentIdenter = HentIdenter(listOf(PdlIdent(ident = "987654321", gruppe = "foo")))
            ), errors = null)

            runBlocking {
                val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                pdlPerson shouldBe null
            }
        }

        it("Skal feile når identer ikke finnes") {
            coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(hentPerson = HentPerson(
                    navn = listOf(Navn("fornavn", "mellomnavn", "etternavn"))
            ),
                    hentIdenter = HentIdenter(emptyList())
            ), errors = null)

            runBlocking {
                val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                pdlPerson shouldBe null
            }
        }
    }
})
