package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import java.time.LocalDate

@KtorExperimentalAPI
class OppgaveClient constructor(private val url: String, private val oidcClient: StsOidcClient, private val httpClient: HttpClient) {
    private suspend fun opprettOppgave(opprettOppgaveRequest: OpprettOppgaveRequest, msgId: String): OpprettOppgaveResponse = retry("opprett_oppgave") {
        httpClient.post<OpprettOppgaveResponse>(url) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            this.header("Authorization", "Bearer ${oidcToken.access_token}")
            this.header("X-Correlation-ID", msgId)
            body = opprettOppgaveRequest
        }
    }

    suspend fun opprettOppgave(
        sakId: String,
        journalpostId: String,
        tildeltEnhetsnr: String,
        aktoerId: String,
        sykmeldingId: String,
        loggingMeta: LoggingMeta
    ): Int {
        val opprettOppgaveRequest = OpprettOppgaveRequest(
                tildeltEnhetsnr = tildeltEnhetsnr,
                aktoerId = aktoerId,
                opprettetAvEnhetsnr = "9999",
                journalpostId = journalpostId,
                behandlesAvApplikasjon = "FS22",
                saksreferanse = sakId,
                beskrivelse = "Papirsykmelding som må legges inn i infotrygd manuelt",
                tema = "SYM",
                oppgavetype = "JFR",
                aktivDato = LocalDate.now(),
                fristFerdigstillelse = LocalDate.now().plusDays(1),
                prioritet = "NORM"
        )
        log.info("Oppretter journalføringsoppgave på enhet $tildeltEnhetsnr, {}", fields(loggingMeta))
        return opprettOppgave(opprettOppgaveRequest, sykmeldingId).id
    }

    suspend fun opprettFordelingsOppgave(
        journalpostId: String,
        tildeltEnhetsnr: String,
        sykmeldingId: String,
        loggingMeta: LoggingMeta
    ): Int {
        val opprettOppgaveRequest = OpprettOppgaveRequest(
            tildeltEnhetsnr = tildeltEnhetsnr,
            opprettetAvEnhetsnr = "9999",
            journalpostId = journalpostId,
            behandlesAvApplikasjon = "FS22",
            beskrivelse = "Fordelingsoppgave for mottatt papirsykmelding som må legges inn i infotrygd manuelt",
            tema = "SYM",
            oppgavetype = "FDR",
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = LocalDate.now().plusDays(1),
            prioritet = "NORM"
        )
        log.info("Oppretter fordelingsoppgave på enhet $tildeltEnhetsnr, {}", fields(loggingMeta))
        return opprettOppgave(opprettOppgaveRequest, sykmeldingId).id
    }
}

data class OpprettOppgaveRequest(
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String? = null,
    val aktoerId: String? = null,
    val journalpostId: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val saksreferanse: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String? = null,
    val tema: String? = null,
    val oppgavetype: String,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate? = null,
    val prioritet: String
)

data class OpprettOppgaveResponse(
    val id: Int
)
