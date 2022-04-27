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
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class NorskHelsenettClientSpek : FunSpec({
    val accessTokenClientMock = mockk<AccessTokenClientV2>()
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

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            jackson {}
        }
        routing {
            get("/syfohelsenettproxy/api/v2/behandlerMedHprNummer") {
                when {
                    call.request.headers["hprNummer"] == "1234" -> call.respond(
                        Behandler(
                            godkjenninger = listOf(Godkjenning(helsepersonellkategori = Kode(true, 1, "verdi"), autorisasjon = Kode(true, 2, "annenVerdi"))),
                            fnr = "12345678910",
                            fornavn = "Fornavn",
                            mellomnavn = null,
                            etternavn = "Etternavn"
                        )
                    )
                    call.request.headers["hprNummer"] == "0" -> call.respond(HttpStatusCode.NotFound, "Behandler finnes ikke")
                    else -> call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                }
            }
        }
    }.start()

    val norskHelsenettClient = NorskHelsenettClient("$mockHttpServerUrl/syfohelsenettproxy", accessTokenClientMock, "resourceId", httpClient)

    afterSpec {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    beforeSpec {
        coEvery { accessTokenClientMock.getAccessTokenV2(any()) } returns "token"
    }

    context("Håndtering av respons fra syfohelsenettproxy") {
        test("Får hente behandler som finnes") {
            val behandler = norskHelsenettClient.finnBehandler("1234", "sykmeldingsId")

            behandler shouldNotBe null
            behandler?.godkjenninger?.size shouldBeEqualTo 1
            behandler?.fornavn shouldBeEqualTo "Fornavn"
            behandler?.mellomnavn shouldBeEqualTo null
            behandler?.etternavn shouldBeEqualTo "Etternavn"
            behandler?.fnr shouldBeEqualTo "12345678910"
        }
        test("Returnerer null for behandler som ikke finnes") {
            val behandler = norskHelsenettClient.finnBehandler("0", "sykmeldingsId")

            behandler shouldBeEqualTo null
        }
    }
})
