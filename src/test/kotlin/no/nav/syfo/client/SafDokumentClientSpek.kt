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
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykSkanningMeta.SkanningmetadataType
import no.nav.syfo.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
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
    val loggingMetadata = LoggingMeta("sykmeldingId","123", "hendelsesId")

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
        }
    }.start()

    val safDokumentClient = SafDokumentClient("$mockHttpServerUrl/saf", stsOidcClientMock, httpClient)

    afterGroup {
        mockServer.stop(1L, 10L, TimeUnit.SECONDS)
    }

    beforeGroup {
        coEvery { stsOidcClientMock.oidcToken() } returns OidcToken("token", "type", 300L)
    }

    /*describe("SafDokumentClient happy-case") {
        it("Mapper mottatt dokument korrekt") {
            var skanningmetadata: SkanningmetadataType? = null
            runBlocking {
                skanningmetadata = safDokumentClient.hentDokument("journalpostId", "dokumentInfoId", "sykmeldingId", loggingMetadata)
            }

            skanningmetadata shouldNotEqual null
            skanningmetadata?.sykemeldinger?.pasient?.fnr shouldEqual "12345678910"
            skanningmetadata?.sykemeldinger?.medisinskVurdering?.hovedDiagnose?.first()?.diagnosekode shouldEqual "900.3"
            skanningmetadata?.sykemeldinger?.medisinskVurdering?.hovedDiagnose?.first()?.diagnose shouldEqual "Skikkelig syk"
            skanningmetadata?.sykemeldinger?.aktivitet?.aktivitetIkkeMulig?.periodeFOMDato shouldEqual LocalDate.of(2019, Month.JANUARY, 10)
            skanningmetadata?.sykemeldinger?.aktivitet?.aktivitetIkkeMulig?.periodeTOMDato shouldEqual LocalDate.of(2019, Month.JANUARY, 14)
            skanningmetadata?.sykemeldinger?.aktivitet?.aktivitetIkkeMulig?.medisinskeArsaker?.medArsakerHindrer shouldEqual 1
            skanningmetadata?.sykemeldinger?.tilbakedatering?.tilbakebegrunnelse shouldEqual "Legevakten\n" +
                "                Sentrum"
            skanningmetadata?.sykemeldinger?.kontaktMedPasient?.behandletDato shouldEqual LocalDate.of(2019, Month.JANUARY, 11)
            skanningmetadata?.sykemeldinger?.behandler?.hpr shouldEqual "12345678"
        }
    }*/
})

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
