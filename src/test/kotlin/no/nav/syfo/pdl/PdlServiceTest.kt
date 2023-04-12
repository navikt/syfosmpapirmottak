package no.nav.syfo.pdl

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
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

class PdlServiceTest : FunSpec({

    val pdlClient = mockkClass(PdlClient::class)
    val accessTokenClientV2 = mockkClass(AccessTokenClientV2::class)
    val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, "littaScope")

    val loggingMeta = LoggingMeta("sykmeldingId", "journalpostId", "hendelsesId")

    beforeEach {
        clearAllMocks()
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
    }

    context("Tests PDL Service") {
        test("Hent person fra pdl uten fortrolig adresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse()

            val person = pdlService.getPdlPerson("01245678901", loggingMeta)
            person?.navn?.fornavn shouldBeEqualTo "fornavn"
            person?.navn?.mellomnavn shouldBeEqualTo null
            person?.navn?.etternavn shouldBeEqualTo "etternavn"
            person?.aktorId shouldBeEqualTo "987654321"
        }

        test("Skal feile når person ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null, null), errors = null)

            val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
            pdlPerson shouldBe null
        }

        test("Skal feile når navn er tom liste") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = emptyList(),
                        adressebeskyttelse = null,
                    ),
                    hentIdenter = HentIdenter(emptyList()),
                ),
                errors = null,
            )

            val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
            pdlPerson shouldBe null
        }

        test("Skal feile når navn ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = null,
                        adressebeskyttelse = null,
                    ),
                    hentIdenter = HentIdenter(listOf(PdlIdent(ident = "987654321", gruppe = "foo"))),
                ),
                errors = null,
            )

            val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
            pdlPerson shouldBe null
        }

        test("Skal feile når identer ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = listOf(Navn("fornavn", "mellomnavn", "etternavn")),
                        adressebeskyttelse = null,
                    ),
                    hentIdenter = HentIdenter(emptyList()),
                ),
                errors = null,
            )

            val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
            pdlPerson shouldBe null
        }
    }
})
