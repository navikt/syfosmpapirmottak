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
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
object AktoerIdClientSpek : Spek({
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

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {}
        }
        routing {
            get("/aktorregister/identer") {
                when {
                    call.request.headers["Nav-Personidenter"] == "aktorId" -> call.respond(mapOf("aktorId" to Aktor(identer = listOf(Ident("fnr", "NorskIdent", true)))))
                    call.request.headers["Nav-Personidenter"] == "fnr" -> call.respond(mapOf("fnr" to Aktor(identer = listOf(Ident("aktorId", "AktoerId", true)))))
                    call.request.headers["Nav-Personidenter"] == "fnrSomMangler" -> call.respond(mapOf("fnrSomMangler" to Aktor(feilmelding = "fnr finnes ikke")))
                    call.request.headers["Nav-Personidenter"] == "aktorIdSomMangler" -> call.respond(mapOf("aktorIdSomMangler" to Aktor(feilmelding = "aktorId finnes ikke")))
                    call.request.headers["Nav-Personidenter"] == "fnrILimbo" -> call.respond(mapOf("fnrILimbo" to Aktor(identer = emptyList())))
                    else -> call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                }
            }
        }
    }.start()

    val aktoerIdClient = AktoerIdClient("$mockHttpServerUrl/aktorregister", stsOidcClientMock, httpClient)

    afterGroup {
        mockServer.stop(1L, 10L, TimeUnit.SECONDS)
    }

    beforeGroup {
        coEvery { stsOidcClientMock.oidcToken() } returns OidcToken("token", "type", 300L)
    }

    describe("AktoerIdClient happy-case") {
        it("Får hente fnr for aktør som finnes") {
            var fnr: String? = null
            runBlocking {
                fnr = aktoerIdClient.finnFnr("aktorId", "sykmeldingsId")
            }

            fnr shouldEqual "fnr"
        }

        it("Får hente aktør for fnr som finnes") {
            var aktorId: String? = null
            runBlocking {
                aktorId = aktoerIdClient.finnAktorid("fnr", "sykmeldingsId")
            }

            aktorId shouldEqual "aktorId"
        }
    }

    describe("AktoerIdClient feilsituasjoner") {
        it("Returnerer null for aktørid der fnr ikke finnes") {
            var fnr: String? = null
            runBlocking {
                fnr = aktoerIdClient.finnFnr("aktorIdSomMangler", "sykmeldingsId")
            }

            fnr shouldEqual null
        }

        it("Returnerer null for fnr der aktørid ikke finnes") {
            var aktorId: String? = null
            runBlocking {
                aktorId = aktoerIdClient.finnAktorid("fnrSomMangler", "sykmeldingsId")
            }

            aktorId shouldEqual null
        }

        it("Kaster feil hvis aktor-objekt mangler og det ikke er feilmelding") {
            assertFailsWith<IllegalStateException> {
                runBlocking {
                    aktoerIdClient.finnAktorid("fnrILimbo", "sykmeldingsId")
                }
            }
        }

        it("Kaster feil hvis aktørregisteret returnerer feilkode") {
            assertFailsWith<IllegalStateException> {
                runBlocking {
                    aktoerIdClient.finnAktorid("tekniskFeil", "sykmeldingsId")
                }
            }
        }
    }
})
