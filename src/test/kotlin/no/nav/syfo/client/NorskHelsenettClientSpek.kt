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
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object NorskHelsenettClientSpek : Spek({
    val accessTokenClientMock = mockk<AccessTokenClient>()
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

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {}
        }
        routing {
            get("/syfohelsenettproxy/api/behandlerMedHprNummer") {
                when {
                    call.request.headers["hprNummer"] == "1234" -> call.respond(Behandler(
                        godkjenninger = listOf(Godkjenning(helsepersonellkategori = Kode(true, 1, "verdi"), autorisasjon = Kode(true, 2, "annenVerdi"))),
                        fnr = "12345678910",
                        fornavn = "Fornavn",
                        mellomnavn = null,
                        etternavn = "Etternavn")
                    )
                    call.request.headers["hprNummer"] == "0" -> call.respond(HttpStatusCode.NotFound, "Behandler finnes ikke")
                    else -> call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                }
            }
        }
    }.start()

    val norskHelsenettClient = NorskHelsenettClient("$mockHttpServerUrl/syfohelsenettproxy", accessTokenClientMock, "resourceId", httpClient)

    afterGroup {
        mockServer.stop(1L, 10L, TimeUnit.SECONDS)
    }

    beforeGroup {
        coEvery { accessTokenClientMock.hentAccessToken(any()) } returns "token"
    }

    describe("Håndtering av respons fra syfohelsenettproxy") {
        it("Får hente behandler som finnes") {
            var behandler: Behandler? = null
            runBlocking {
                behandler = norskHelsenettClient.finnBehandler("1234", "sykmeldingsId")
            }

            behandler shouldNotBe null
            behandler?.godkjenninger?.size shouldEqual 1
            behandler?.fornavn shouldEqual "Fornavn"
            behandler?.mellomnavn shouldEqual null
            behandler?.etternavn shouldEqual "Etternavn"
            behandler?.fnr shouldEqual "12345678910"
        }
        it("Returnerer null for behandler som ikke finnes") {
            var behandler: Behandler? = null
            runBlocking {
                behandler = norskHelsenettClient.finnBehandler("0", "sykmeldingsId")
            }

            behandler shouldEqual null
        }
    }
})
