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
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.LoggingMeta
import no.nav.syfo.domain.OppgaveResultat
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
object OppgaveClientSpek : Spek({
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
            get("/oppgave") {
                when {
                    call.request.queryParameters["oppgavetype"] == "JFR" && call.request.queryParameters["journalpostId"] == "123" -> call.respond(OppgaveResponse(1,
                        listOf(Oppgave(1, "9999",
                            "123456789", "123", "2",
                            "SYM", "JFR"))))
                    call.request.queryParameters["oppgavetype"] == "FDR" && call.request.queryParameters["journalpostId"] == "123" -> call.respond(OppgaveResponse(1,
                        listOf(Oppgave(1, "9999",
                            null, "123", "2",
                            "SYM", "FDR"))))
                    call.request.queryParameters["oppgavetype"] == "JFR" && call.request.queryParameters["journalpostId"] == "987" -> call.respond(OppgaveResponse(0, emptyList()))
                    call.request.queryParameters["oppgavetype"] == "FDR" && call.request.queryParameters["journalpostId"] == "987" -> call.respond(OppgaveResponse(0, emptyList()))
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }
            post("/oppgave") {
                call.respond(OpprettOppgaveResponse(42))
            }
        }
    }.start()

    val oppgaveClient = OppgaveClient("$mockHttpServerUrl/oppgave", stsOidcClientMock, httpClient)

    afterGroup {
        mockServer.stop(1L, 10L, TimeUnit.SECONDS)
    }

    beforeGroup {
        coEvery { stsOidcClientMock.oidcToken() } returns OidcToken("token", "type", 300L)
    }

    describe("OppgaveClient oppretter oppgave når det ikke finnes fra før") {
        it("Oppretter ikke JFR-oppgave hvis finnes fra før") {
            var oppgave: OppgaveResultat? = null
            runBlocking {
                oppgave = oppgaveClient.opprettOppgave("sakId", "123", "9999", "123456789", false, "sykmeldingId", loggingMetadata)
            }

            oppgave?.oppgaveId shouldEqual 1
            oppgave?.duplikat shouldEqual true
        }
        it("Oppretter ikke FDR-oppgave hvis finnes fra før") {
            var oppgave: OppgaveResultat? = null
            runBlocking {
                oppgave = oppgaveClient.opprettFordelingsOppgave("123", "9999", false, "sykmeldingId", loggingMetadata)
            }

            oppgave?.oppgaveId shouldEqual 1
            oppgave?.duplikat shouldEqual true
        }
        it("Oppretter JFR-oppgave hvis det ikke finnes fra før") {
            var oppgave: OppgaveResultat? = null
            runBlocking {
                oppgave = oppgaveClient.opprettOppgave("sakId", "987", "9999", "123456789", false, "sykmeldingId", loggingMetadata)
            }

            oppgave?.oppgaveId shouldEqual 42
            oppgave?.duplikat shouldEqual false
        }
        it("Oppretter FDR-oppgave hvis det ikke finnes fra før") {
            var oppgave: OppgaveResultat? = null
            runBlocking {
                oppgave = oppgaveClient.opprettFordelingsOppgave("987", "9999", false, "sykmeldingId", loggingMetadata)
            }

            oppgave?.oppgaveId shouldEqual 42
            oppgave?.duplikat shouldEqual false
        }
    }
})
