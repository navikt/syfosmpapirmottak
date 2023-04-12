package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.internal.assertFails
import org.amshove.kluent.shouldBeEqualTo
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

class OppgaveClientSpek : FunSpec({
    val accessTokenClient = mockk<AccessTokenClientV2>()
    val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            maxRetries = 2
            delayMillis { retry ->
                retry * 5L
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
            get("/oppgave") {
                when {
                    call.request.queryParameters["oppgavetype"] == "JFR" && call.request.queryParameters["journalpostId"] == "123" -> call.respond(
                        OppgaveResponse(
                            1,
                            listOf(
                                Oppgave(
                                    1,
                                    "9999",
                                    "123456789",
                                    "123",
                                    "SYM",
                                    "JFR",
                                    "",
                                ),
                            ),
                        ),
                    )
                    call.request.queryParameters["oppgavetype"] == "FDR" && call.request.queryParameters["journalpostId"] == "123" -> call.respond(
                        OppgaveResponse(
                            1,
                            listOf(
                                Oppgave(
                                    1,
                                    "9999",
                                    null,
                                    "123",
                                    "SYM",
                                    "FDR",
                                    "",
                                ),
                            ),
                        ),
                    )
                    call.request.queryParameters["oppgavetype"] == "JFR" && call.request.queryParameters["journalpostId"] == "987" -> call.respond(OppgaveResponse(0, emptyList()))
                    call.request.queryParameters["oppgavetype"] == "FDR" && call.request.queryParameters["journalpostId"] == "987" -> call.respond(OppgaveResponse(0, emptyList()))
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }
            post("/oppgave") {
                when {
                    call.request.headers["X-Correlation-ID"] == "feiler" -> call.respond(HttpStatusCode.BadRequest, OppgaveFeilrespons(UUID.randomUUID().toString(), "Fant ikke person"))
                    else -> call.respond(HttpStatusCode.Created, OpprettOppgaveResponse(id = 42, versjon = 1))
                }
            }
        }
    }.start()

    val oppgaveClient = OppgaveClient("$mockHttpServerUrl/oppgave", accessTokenClient, httpClient, "scope", "prod-gcp")

    afterSpec {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    beforeSpec {
        coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"
    }

    context("OppgaveClient oppretter oppgave når det ikke finnes fra før") {
        test("Oppretter ikke JFR-oppgave hvis finnes fra før") {
            val oppgave = oppgaveClient.opprettOppgave("123", "123456789", false, "sykmeldingId", loggingMetadata)

            oppgave.oppgaveId shouldBeEqualTo 1
            oppgave.duplikat shouldBeEqualTo true
        }
        test("Oppretter ikke FDR-oppgave hvis finnes fra før") {
            val oppgave = oppgaveClient.opprettFordelingsOppgave("123", false, "sykmeldingId", loggingMetadata)

            oppgave.oppgaveId shouldBeEqualTo 1
            oppgave.duplikat shouldBeEqualTo true
        }
        test("Oppretter JFR-oppgave hvis det ikke finnes fra før") {
            val oppgave = oppgaveClient.opprettOppgave("987", "123456789", false, "sykmeldingId", loggingMetadata)

            oppgave.oppgaveId shouldBeEqualTo 42
            oppgave.duplikat shouldBeEqualTo false
        }
        test("Oppretter FDR-oppgave hvis det ikke finnes fra før") {
            val oppgave = oppgaveClient.opprettFordelingsOppgave("987", false, "sykmeldingId", loggingMetadata)

            oppgave.oppgaveId shouldBeEqualTo 42
            oppgave.duplikat shouldBeEqualTo false
        }
        test("Opprett oppgave feiler hvis Oppgave svarer med feilmelding") {
            assertFails {
                oppgaveClient.opprettOppgave("987", "123456789", false, "feiler", loggingMetadata)
            }
        }
    }

    context("OppgaveClient setter fristFerdigstillelse til forvenetet journalføringsfrist") {
        test("Setter fristFerdigstillelse til mandag, viss oppgaven kom inn på søndag") {
            val fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.of(2019, 12, 1))
            fristFerdigstillelse.dayOfWeek shouldBeEqualTo DayOfWeek.MONDAY
        }

        test("Setter fristFerdigstillelse til mandag, viss oppgaven kom inn på lørdag") {
            val fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.of(2019, 11, 30))
            fristFerdigstillelse.dayOfWeek shouldBeEqualTo DayOfWeek.MONDAY
        }

        test("Setter fristFerdigstillelse til mandag, viss oppgaven kom inn på fredag") {
            val fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.of(2019, 11, 29))
            fristFerdigstillelse.dayOfWeek shouldBeEqualTo DayOfWeek.MONDAY
        }

        test("Setter fristFerdigstillelse til tirsdag, viss oppgaven kom inn på mandag") {
            val fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.of(2019, 12, 2))
            fristFerdigstillelse.dayOfWeek shouldBeEqualTo DayOfWeek.TUESDAY
        }
    }
})

private data class OppgaveFeilrespons(
    val uuid: String,
    val feilmelding: String,
)
