package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.domain.OppgaveResultat
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import java.time.DayOfWeek
import java.time.LocalDate

class OppgaveClient(
    private val url: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val httpClient: HttpClient,
    private val scope: String,
    private val cluster: String
) {
    private suspend fun opprettOppgave(opprettOppgaveRequest: OpprettOppgaveRequest, msgId: String): OpprettOppgaveResponse {
        try {
            return httpClient.post(url) {
                contentType(ContentType.Application.Json)
                val token = accessTokenClientV2.getAccessTokenV2(scope)
                header("Authorization", "Bearer $token")
                header("X-Correlation-ID", msgId)
                setBody(opprettOppgaveRequest)
            }.body<OpprettOppgaveResponse>()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved oppretting av oppgave for sykmeldingId $msgId", e)
            throw e
        }
    }

    suspend fun hentOppgave(oppgavetype: String, journalpostId: String, msgId: String): OppgaveResponse {
        return httpClient.get(url) {
            val token = accessTokenClientV2.getAccessTokenV2(scope)
            header("Authorization", "Bearer $token")
            header("X-Correlation-ID", msgId)
            parameter("tema", "SYM")
            parameter("oppgavetype", oppgavetype)
            parameter("journalpostId", journalpostId)
            parameter("statuskategori", "AAPEN")
            parameter("sorteringsrekkefolge", "ASC")
            parameter("sorteringsfelt", "FRIST")
            parameter("limit", "10")
        }.body<OppgaveResponse>()
    }

    suspend fun opprettOppgave(
        journalpostId: String,
        aktoerId: String,
        gjelderUtland: Boolean,
        sykmeldingId: String,
        loggingMeta: LoggingMeta
    ): OppgaveResultat {
        val oppgaveResponse = hentOppgave(oppgavetype = "JFR", journalpostId = journalpostId, msgId = sykmeldingId)
        if (oppgaveResponse.antallTreffTotalt > 0) {
            log.info("Det finnes allerede journalføringsoppgave for journalpost $journalpostId, {}", fields(loggingMeta))
            return OppgaveResultat(oppgaveResponse.oppgaver.first().id, true)
        }
        val behandlingstype = if (gjelderUtland) {
            log.info("Gjelder utland, {}", fields(loggingMeta))
            "ae0106"
        } else {
            null
        }
        val behandlesAvApplikasjon = if (gjelderUtland && cluster == "dev-gcp") {
            log.info("Oppgave skal behandles i syk-dig, {}", fields(loggingMeta))
            "SMD"
        } else {
            "FS22"
        }
        val beskrivelse = if (gjelderUtland && cluster == "dev-gcp") {
            "Manuell registrering av utenlandsk sykmelding"
        } else {
            "Papirsykmelding som må legges inn i infotrygd manuelt"
        }
        val opprettOppgaveRequest = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            opprettetAvEnhetsnr = "9999",
            journalpostId = journalpostId,
            behandlesAvApplikasjon = behandlesAvApplikasjon,
            beskrivelse = beskrivelse,
            tema = "SYM",
            oppgavetype = "JFR",
            behandlingstype = behandlingstype,
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.now()),
            prioritet = "NORM"
        )
        log.info("Oppretter journalføringsoppgave {}", fields(loggingMeta))
        return OppgaveResultat(opprettOppgave(opprettOppgaveRequest, sykmeldingId).id, false)
    }

    suspend fun opprettFordelingsOppgave(
        journalpostId: String,
        gjelderUtland: Boolean,
        sykmeldingId: String,
        loggingMeta: LoggingMeta
    ): OppgaveResultat {
        val oppgaveResponse = hentOppgave(oppgavetype = "FDR", journalpostId = journalpostId, msgId = sykmeldingId)
        if (oppgaveResponse.antallTreffTotalt > 0) {
            log.info("Det finnes allerede fordelingsoppgave for journalpost $journalpostId, {}", fields(loggingMeta))
            return OppgaveResultat(oppgaveResponse.oppgaver.first().id, true)
        }
        var behandlingstype: String? = null
        if (gjelderUtland) {
            log.info("Gjelder utland, {}", fields(loggingMeta))
            behandlingstype = "ae0106"
        }
        val opprettOppgaveRequest = OpprettOppgaveRequest(
            opprettetAvEnhetsnr = "9999",
            journalpostId = journalpostId,
            behandlesAvApplikasjon = "FS22",
            beskrivelse = "Fordelingsoppgave for mottatt papirsykmelding som må legges inn i infotrygd manuelt",
            tema = "SYM",
            oppgavetype = "FDR",
            behandlingstype = behandlingstype,
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.now()),
            prioritet = "NORM"
        )
        log.info("Oppretter fordelingsoppgave {}", fields(loggingMeta))
        return OppgaveResultat(opprettOppgave(opprettOppgaveRequest, sykmeldingId).id, false)
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
    val prioritet: String
)

data class OpprettOppgaveResponse(
    val id: Int
)

data class OppgaveResponse(
    val antallTreffTotalt: Int,
    val oppgaver: List<Oppgave>
)

data class Oppgave(
    val id: Int,
    val tildeltEnhetsnr: String?,
    val aktoerId: String?,
    val journalpostId: String?,
    val tema: String?,
    val oppgavetype: String?,
    val behandlesAvApplikasjon: String?
)

fun finnFristForFerdigstillingAvOppgave(today: LocalDate): LocalDate {
    return when (today.dayOfWeek) {
        DayOfWeek.FRIDAY -> today.plusDays(3)
        DayOfWeek.SATURDAY -> today.plusDays(2)
        else -> today.plusDays(1)
    }
}
