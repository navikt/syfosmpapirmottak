package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.math.BigInteger
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month
import java.util.concurrent.TimeUnit

object SafDokumentClientSpek : Spek({
    val stsOidcClientMock = mockk<StsOidcClient>()
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
    val loggingMetadata = LoggingMeta("sykmeldingId", "123", "hendelsesId")

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {}
        }
        routing {
            get("/saf/rest/hentdokument/journalpostId/dokumentInfoId/ORIGINAL") {
                call.respond(getFileAsString("src/test/resources/ocr-eksempel.xml"))
            }
            get("/saf/rest/hentdokument/journalpostId/dokumentInfoIdUgyldigDok/ORIGINAL") {
                call.respond("<ikke engang gyldig xml!>")
            }
            get("/saf/rest/hentdokument/journalpostId/dokumentInfoIdFinnesIkke/ORIGINAL") {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }.start()

    val safDokumentClient = SafDokumentClient("$mockHttpServerUrl/saf", stsOidcClientMock, httpClient)

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
    }

    beforeGroup {
        coEvery { stsOidcClientMock.oidcToken() } returns OidcToken("token", "type", 300L)
    }

    describe("SafDokumentClient håndterer respons korrekt") {
        it("Mapper mottatt dokument korrekt") {
            var skanningmetadata: Skanningmetadata? = null
            runBlocking {
                skanningmetadata = safDokumentClient.hentDokument("journalpostId", "dokumentInfoId", "sykmeldingId", loggingMetadata)
            }

            skanningmetadata shouldNotBeEqualTo null
            skanningmetadata?.sykemeldinger?.pasient?.fnr shouldBeEqualTo "12345678910"
            skanningmetadata?.sykemeldinger?.medisinskVurdering?.hovedDiagnose?.first()?.diagnosekode shouldBeEqualTo "900.3"
            skanningmetadata?.sykemeldinger?.medisinskVurdering?.hovedDiagnose?.first()?.diagnose shouldBeEqualTo "Skikkelig syk"
            skanningmetadata?.sykemeldinger?.aktivitet?.aktivitetIkkeMulig?.periodeFOMDato shouldBeEqualTo LocalDate.of(2019, Month.JANUARY, 10)
            skanningmetadata?.sykemeldinger?.aktivitet?.aktivitetIkkeMulig?.periodeTOMDato shouldBeEqualTo LocalDate.of(2019, Month.JANUARY, 14)
            skanningmetadata?.sykemeldinger?.aktivitet?.aktivitetIkkeMulig?.medisinskeArsaker?.isMedArsakerHindrer shouldBeEqualTo true
            skanningmetadata?.sykemeldinger?.tilbakedatering?.tilbakebegrunnelse shouldBeEqualTo "Legevakten\n" +
                "                Sentrum"
            skanningmetadata?.sykemeldinger?.kontaktMedPasient?.behandletDato shouldBeEqualTo LocalDate.of(2019, Month.JANUARY, 11)
            skanningmetadata?.sykemeldinger?.behandler?.hpr shouldBeEqualTo BigInteger("12345678")
        }

        it("Returnerer null hvis dokumentet ikke er i henhold til skjema (det skal ikke kastes feil)") {
            var skanningmetadata: Skanningmetadata? = null
            runBlocking {
                skanningmetadata = safDokumentClient.hentDokument("journalpostId", "dokumentInfoIdUgyldigDok", "sykmeldingId", loggingMetadata)
            }

            skanningmetadata shouldBeEqualTo null
        }

        it("Kaster SafNotFoundException hvis dokumentet ikke finnes") {
            var skanningmetadata: Skanningmetadata? = null
            assertFailsWith<SafNotFoundException> {
                runBlocking {
                    skanningmetadata = safDokumentClient.hentDokument("journalpostId", "dokumentInfoIdFinnesIkke", "sykmeldingId", loggingMetadata)
                }
            }
            skanningmetadata shouldBeEqualTo null
        }
    }
})

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
