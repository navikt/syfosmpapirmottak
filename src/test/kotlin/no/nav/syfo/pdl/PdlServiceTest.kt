package no.nav.syfo.pdl

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
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
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object PdlServiceTest : Spek({

    val pdlClient = mockkClass(PdlClient::class)
    val accessTokenClientV2 = mockkClass(AccessTokenClientV2::class)
    val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, "littaScope")

    val loggingMeta = LoggingMeta("sykmeldingId", "journalpostId", "hendelsesId")

    beforeEachTest {
        clearAllMocks()
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
    }

    describe("Tests PDL Service") {
        it("Hent person fra pdl uten fortrolig adresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse()

            runBlocking {
                val person = pdlService.getPdlPerson("01245678901", loggingMeta)
                person?.navn?.fornavn shouldBeEqualTo "fornavn"
                person?.navn?.mellomnavn shouldBeEqualTo null
                person?.navn?.etternavn shouldBeEqualTo "etternavn"
                person?.aktorId shouldBeEqualTo "987654321"
            }
        }

        it("Skal feile når person ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null, null), errors = null)

            runBlocking {
                val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                pdlPerson shouldBe null
            }
        }

        it("Skal feile når navn er tom liste") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = emptyList(), adressebeskyttelse = null
                    ),
                    hentIdenter = HentIdenter(emptyList())
                ),
                errors = null
            )

            runBlocking {
                val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                pdlPerson shouldBe null
            }
        }

        it("Skal feile når navn ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = null, adressebeskyttelse = null
                    ),
                    hentIdenter = HentIdenter(listOf(PdlIdent(ident = "987654321", gruppe = "foo")))
                ),
                errors = null
            )

            runBlocking {
                val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                pdlPerson shouldBe null
            }
        }

        it("Skal feile når identer ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = listOf(Navn("fornavn", "mellomnavn", "etternavn")),
                        adressebeskyttelse = null
                    ),
                    hentIdenter = HentIdenter(emptyList())
                ),
                errors = null
            )

            runBlocking {
                val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                pdlPerson shouldBe null
            }
        }
    }
})
