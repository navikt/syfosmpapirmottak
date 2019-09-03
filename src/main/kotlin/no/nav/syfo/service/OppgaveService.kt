package no.nav.syfo.service

import com.ctc.wstx.exc.WstxException
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.STANDARD_NAV_ENHET
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.pip.diskresjonskode.meldinger.WSHentDiskresjonskodeRequest
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.FinnBehandlendeEnhetListeUgyldigInput
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.ArbeidsfordelingKriterier
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Diskresjonskoder
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Geografi
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Oppgavetyper
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Tema
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeRequest
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeResponse
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.GeografiskTilknytning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import java.io.IOException

class OppgaveService @KtorExperimentalAPI constructor(
    val oppgaveClient: OppgaveClient,
    val personV3: PersonV3,
    val diskresjonskodeV1: DiskresjonskodePortType,
    val arbeidsfordelingV1: ArbeidsfordelingV1
) {
    @KtorExperimentalAPI
    suspend fun opprettOppgave(
        fnrPasient: String,
        aktoerIdPasient: String,
        sakId: String,
        journalpostId: String,
        trackingId: String,
        loggingMeta: LoggingMeta
    ): Int {

        log.info("Oppretter oppgave for {}", fields(loggingMeta))
        val geografiskTilknytning = fetchGeografiskTilknytning(fnrPasient, loggingMeta)
        val diskresjonsKode = fetchDiskresjonsKode(fnrPasient, loggingMeta)
        val enhetsListe = fetchBehandlendeEnhet(lagFinnBehandlendeEnhetListeRequest(geografiskTilknytning.geografiskTilknytning, diskresjonsKode), loggingMeta)

        val behandlerEnhetsId = enhetsListe?.behandlendeEnhetListe?.firstOrNull()?.enhetId ?: run {
            log.error("Unable to find a NAV enhet, defaulting to $STANDARD_NAV_ENHET {}", fields(loggingMeta))
            STANDARD_NAV_ENHET
        }
        return oppgaveClient.opprettOppgave(sakId, journalpostId, behandlerEnhetsId,
                aktoerIdPasient, trackingId)
    }

    @KtorExperimentalAPI
    suspend fun opprettFordelingsOppgave(
        journalpostId: String,
        trackingId: String,
        loggingMeta: LoggingMeta
    ): Int {

        log.info("Oppretter fordelingsoppgave for {}", fields(loggingMeta))
        val fordelingsenheter = fetchBehandlendeEnhet(lagFinnBehandlendeEnhetListeRequestForFordelingsenhet(), loggingMeta)

        val behandlerEnhetsId = fordelingsenheter?.behandlendeEnhetListe?.firstOrNull()?.enhetId ?: run {
            log.error("Unable to find a NAV enhet, defaulting to $STANDARD_NAV_ENHET {}", fields(loggingMeta))
            STANDARD_NAV_ENHET
        }
        return oppgaveClient.opprettFordelingsOppgave(journalpostId, behandlerEnhetsId, trackingId)
    }

    suspend fun fetchGeografiskTilknytning(patientFnr: String, loggingMeta: LoggingMeta): HentGeografiskTilknytningResponse =
            retry(
                    callName = "tps_hent_geografisktilknytning",
                    retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                    legalExceptions = *arrayOf(IOException::class, WstxException::class)
            ) {
                try {
                    personV3.hentGeografiskTilknytning(
                        HentGeografiskTilknytningRequest().withAktoer(
                            PersonIdent().withIdent(
                                NorskIdent()
                                    .withIdent(patientFnr)
                                    .withType(Personidenter().withValue("FNR"))
                            )
                        )
                    )
                } catch (e: Exception) {
                    log.warn("Kunne ikke hente person fra TPS ${e.message}", fields(loggingMeta))
                    throw e
                }
            }

    fun lagFinnBehandlendeEnhetListeRequest(tilknytting: GeografiskTilknytning?, patientDiskresjonsKode: String?): FinnBehandlendeEnhetListeRequest =
        FinnBehandlendeEnhetListeRequest().apply {
            arbeidsfordelingKriterier = ArbeidsfordelingKriterier().apply {
                if (tilknytting?.geografiskTilknytning != null) {
                    geografiskTilknytning = Geografi().apply {
                        value = tilknytting.geografiskTilknytning
                    }
                }
                tema = Tema().apply {
                    value = "SYM"
                }
                oppgavetype = Oppgavetyper().apply {
                    value = "JFR"
                }
                if (!patientDiskresjonsKode.isNullOrBlank()) {
                    diskresjonskode = Diskresjonskoder().apply {
                        value = patientDiskresjonsKode
                    }
                }
            }
        }

    fun lagFinnBehandlendeEnhetListeRequestForFordelingsenhet(): FinnBehandlendeEnhetListeRequest =
        FinnBehandlendeEnhetListeRequest().apply {
            arbeidsfordelingKriterier = ArbeidsfordelingKriterier().apply {
                tema = Tema().apply {
                    value = "SYM"
                }
                oppgavetype = Oppgavetyper().apply {
                    value = "FDR"
                }
            }
        }

    suspend fun fetchBehandlendeEnhet(finnBehandlendeEnhetListeRequest: FinnBehandlendeEnhetListeRequest, loggingMeta: LoggingMeta): FinnBehandlendeEnhetListeResponse? =
            retry(callName = "finn_nav_kontor",
                    retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                    legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
                try {
                    arbeidsfordelingV1.finnBehandlendeEnhetListe(finnBehandlendeEnhetListeRequest)
                } catch (e: FinnBehandlendeEnhetListeUgyldigInput) {
                    log.warn("Ugyldig input ved henting av behandlende enhet fra Norg2 ${e.message}", fields(loggingMeta))
                    return@retry null
                } catch (e: Exception) {
                    log.warn("Kunne ikke hente behandlende enhet fra Norg2 ${e.message}", fields(loggingMeta))
                    throw e
                }
            }

    suspend fun fetchDiskresjonsKode(pasientFNR: String, loggingMeta: LoggingMeta): String? {
        val diskresjonskodeSomTall: String? = retry(callName = "tps_diskresjonskode",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
            legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            try {
                diskresjonskodeV1.hentDiskresjonskode(WSHentDiskresjonskodeRequest().withIdent(pasientFNR)).diskresjonskode
            } catch (e: Exception) {
                log.warn("Kunne ikke hente diskresjonskode fra TPS ${e.message}", fields(loggingMeta))
                throw e
            }
        }
        return diskresjonskodeSomTall?.let {
            when (diskresjonskodeSomTall) {
                "6" -> "SPSF"
                "7" -> "SPFO"
                else -> null
            }
        }
    }
}
