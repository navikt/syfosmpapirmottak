package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.DayOfWeek
import java.time.LocalDate
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.log
import no.nav.syfo.securelog
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.utland.NAV_UTLAND

data class OppgaveValues(
    val tildeltEnhetsnr: String?,
    val behandlesAvApplikasjon: String?,
    val behandlingsType: String?,
    val beskrivelse: String,
)

class OppgaveClient(
    private val url: String,
    private val accessTokenClientV2: AzureAdV2Client,
    private val httpClient: HttpClient,
    private val scope: String,
    private val cluster: String,
) {
    private suspend fun opprettOppgave(
        opprettOppgaveRequest: OpprettOppgaveRequest,
        msgId: String
    ): OpprettOppgaveResponse {
        val accessToken = accessTokenClientV2.getAccessToken(scope)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for Oppgave")
        }

        val httpResponse: HttpResponse =
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${accessToken.accessToken}")
                header("X-Correlation-ID", msgId)
                setBody(opprettOppgaveRequest)
            }
        return when (httpResponse.status) {
            HttpStatusCode.Created -> httpResponse.body<OpprettOppgaveResponse>()
            else -> {
                log.error(
                    "Noe gikk galt ved oppretting av oppgave for sykmeldingId $msgId: ${httpResponse.status}, ${httpResponse.body<String>()}"
                )
                throw RuntimeException(
                    "Noe gikk galt ved oppretting av oppgave, responskode ${httpResponse.status}"
                )
            }
        }
    }

    suspend fun hentOppgave(
        oppgavetype: String,
        journalpostId: String,
        msgId: String
    ): OppgaveResponse {
        val accessToken = accessTokenClientV2.getAccessToken(scope)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for Oppgave")
        }

        val response: HttpResponse =
            httpClient.get(url) {
                header("Authorization", "Bearer ${accessToken.accessToken}")
                header("X-Correlation-ID", msgId)
                parameter("tema", "SYM")
                parameter("oppgavetype", oppgavetype)
                parameter("journalpostId", journalpostId)
                parameter("statuskategori", "AAPEN")
                parameter("sorteringsrekkefolge", "ASC")
                parameter("sorteringsfelt", "FRIST")
                parameter("limit", "10")
            }

        securelog.info(
            "Response from oppgave status: ${response.status} and body: ${response.bodyAsText()}"
        )

        return response.body<OppgaveResponse>()
    }

    suspend fun getOppgaveValues(gjelderUtland: Boolean): OppgaveValues {
        if (gjelderUtland) {
            return OppgaveValues(
                tildeltEnhetsnr = NAV_UTLAND,
                behandlesAvApplikasjon = "SMD",
                behandlingsType = "ae0106",
                beskrivelse = "Manuell registrering av utenlandsk sykmelding"
            )
        } else {
            return OppgaveValues(
                tildeltEnhetsnr = null,
                behandlesAvApplikasjon = "FS22",
                behandlingsType = null,
                beskrivelse = "Manuell registrering av sykmelding"
            )
        }
    }

    suspend fun opprettOppgave(
        journalpostId: String,
        aktoerId: String,
        gjelderUtland: Boolean,
        sykmeldingId: String,
        loggingMeta: LoggingMeta,
        type: String = "JFR"
    ): OppgaveResultat {
        val oppgaveResponse =
            hentOppgave(oppgavetype = type, journalpostId = journalpostId, msgId = sykmeldingId)
        if (oppgaveResponse.antallTreffTotalt > 0) {
            log.info(
                "Det finnes allerede journalføringsoppgave for journalpost $journalpostId, {}",
                fields(loggingMeta)
            )
            return OppgaveResultat(
                oppgaveId = oppgaveResponse.oppgaver.first().id,
                duplikat = true,
                tildeltEnhetsnr = oppgaveResponse.oppgaver.first().tildeltEnhetsnr,
            )
        }

        val oppgaveValues = getOppgaveValues(gjelderUtland)

        val opprettOppgaveRequest =
            OpprettOppgaveRequest(
                tildeltEnhetsnr = oppgaveValues.tildeltEnhetsnr,
                aktoerId = aktoerId,
                opprettetAvEnhetsnr = "9999",
                journalpostId = journalpostId,
                behandlesAvApplikasjon = oppgaveValues.behandlesAvApplikasjon,
                beskrivelse = oppgaveValues.tildeltEnhetsnr,
                tema = "SYM",
                oppgavetype = "JFR",
                behandlingstype = oppgaveValues.behandlingsType,
                aktivDato = LocalDate.now(),
                fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.now()),
                prioritet = "NORM",
            )
        log.info("Oppretter journalføringsoppgave {}", fields(loggingMeta))
        val opprettetOppgave = opprettOppgave(opprettOppgaveRequest, sykmeldingId)

        return OppgaveResultat(
            oppgaveId = opprettetOppgave.id,
            duplikat = false,
            tildeltEnhetsnr = opprettetOppgave.tildeltEnhetsnr,
        )
    }

    suspend fun opprettFordelingsOppgave(
        journalpostId: String,
        gjelderUtland: Boolean,
        sykmeldingId: String,
        loggingMeta: LoggingMeta,
    ): OppgaveResultat {
        val oppgaveResponse =
            hentOppgave(oppgavetype = "FDR", journalpostId = journalpostId, msgId = sykmeldingId)
        if (oppgaveResponse.antallTreffTotalt > 0) {
            log.info(
                "Det finnes allerede fordelingsoppgave for journalpost $journalpostId, {}",
                fields(loggingMeta)
            )
            return OppgaveResultat(
                oppgaveId = oppgaveResponse.oppgaver.first().id,
                duplikat = true,
                tildeltEnhetsnr = oppgaveResponse.oppgaver.first().tildeltEnhetsnr,
            )
        }
        var behandlingstype: String? = null
        if (gjelderUtland) {
            log.info("Gjelder utland, {}", fields(loggingMeta))
            behandlingstype = "ae0106"
        }
        val opprettOppgaveRequest =
            OpprettOppgaveRequest(
                opprettetAvEnhetsnr = "9999",
                journalpostId = journalpostId,
                behandlesAvApplikasjon = "FS22",
                beskrivelse =
                    "Registrer manuelt mottatt papirsykmelding. \n" +
                        "Når journalført på korrekt fødselsnummer: Åpne https://syk-dig.intern.dev.nav.no/registrer-sykmelding \n" +
                        "tast inn journalpost-ID, hent og opprett sykmelding. Den digitaliseres og legges automatisk inn i Infotrygd.",
                tema = "SYM",
                oppgavetype = "FDR",
                behandlingstype = behandlingstype,
                aktivDato = LocalDate.now(),
                fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.now()),
                prioritet = "NORM",
            )
        log.info("Oppretter fordelingsoppgave {}", fields(loggingMeta))
        val opprettetOppgave = opprettOppgave(opprettOppgaveRequest, sykmeldingId)
        return OppgaveResultat(
            oppgaveId = opprettetOppgave.id,
            duplikat = false,
            tildeltEnhetsnr = opprettetOppgave.tildeltEnhetsnr,
        )
    }
}

data class OpprettOppgaveRequest(
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String? = null,
    val aktoerId: String? = null,
    val journalpostId: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String? = null,
    val tema: String? = null,
    val oppgavetype: String,
    val behandlingstype: String? = null,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate? = null,
    val prioritet: String,
)

data class OppdaterOppgaveRequest(
    val id: Int,
    val versjon: Int,
    val behandlesAvApplikasjon: String,
)

data class OpprettOppgaveResponse(
    val id: Int,
    val versjon: Int,
    val tildeltEnhetsnr: String? = null,
)

data class OppgaveResponse(
    val antallTreffTotalt: Int,
    val oppgaver: List<Oppgave>,
)

data class Oppgave(
    val id: Int,
    val tildeltEnhetsnr: String?,
    val aktoerId: String?,
    val journalpostId: String?,
    val tema: String?,
    val oppgavetype: String?,
    val behandlesAvApplikasjon: String?,
)

fun finnFristForFerdigstillingAvOppgave(today: LocalDate): LocalDate {
    return when (today.dayOfWeek) {
        DayOfWeek.FRIDAY -> today.plusDays(3)
        DayOfWeek.SATURDAY -> today.plusDays(2)
        else -> today.plusDays(1)
    }
}
