package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import java.math.BigInteger
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month
import java.util.concurrent.TimeUnit

class SafDokumentClientSpek : FunSpec({
    val accessTokenClientV2 = mockk<AccessTokenClientV2>()
    val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            delayMillis { retry ->
                retry * 50L
            }
        }
    }
    val loggingMetadata = LoggingMeta("sykmeldingId", "123", "hendelsesId")

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
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

    val safDokumentClient = SafDokumentClient("$mockHttpServerUrl/saf", accessTokenClientV2, "scope", httpClient)

    afterSpec {
        mockServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
    }

    beforeSpec {
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
    }

    context("SafDokumentClient håndterer respons korrekt") {
        test("Mapper mottatt dokument korrekt") {
            val skanningmetadata = safDokumentClient.hentDokument("journalpostId", "dokumentInfoId", "sykmeldingId", loggingMetadata)

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

        test("Returnerer null hvis dokumentet ikke er i henhold til skjema (det skal ikke kastes feil)") {
            val skanningmetadata = safDokumentClient.hentDokument("journalpostId", "dokumentInfoIdUgyldigDok", "sykmeldingId", loggingMetadata)

            skanningmetadata shouldBeEqualTo null
        }

        test("Kaster SafNotFoundException hvis dokumentet ikke finnes") {
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
