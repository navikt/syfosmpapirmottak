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
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.*
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
    suspend fun createOppgave(
        fnrPasient: String,
        aktoerIdPasient: String,
        sakId: String,
        journalpostId: String,
        trackingId: String,
        loggingMeta: LoggingMeta
    ): Int {

        log.info("Oppretter oppgave for {}", fields(loggingMeta))
        val geografiskTilknytning = fetchGeografiskTilknytning(fnrPasient)
        val diskresjonsKode = fetchDiskresjonsKode(fnrPasient)
        val enhetsListe = fetchBehandlendeEnhet(geografiskTilknytning.geografiskTilknytning, diskresjonsKode)

        val behandlerEnhetsId = enhetsListe?.behandlendeEnhetListe?.firstOrNull()?.enhetId ?: run {
            log.error("Unable to find a NAV enhet, defaulting to $STANDARD_NAV_ENHET {}", fields(loggingMeta))
            STANDARD_NAV_ENHET
        }
        return oppgaveClient.opprettOppgave(sakId, journalpostId, behandlerEnhetsId,
                aktoerIdPasient, trackingId)
    }

    suspend fun fetchGeografiskTilknytning(patientFnr: String): HentGeografiskTilknytningResponse =
            retry(
                    callName = "tps_hent_geografisktilknytning",
                    retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                    legalExceptions = *arrayOf(IOException::class, WstxException::class)
            ) {
                personV3.hentGeografiskTilknytning(
                        HentGeografiskTilknytningRequest().withAktoer(
                                PersonIdent().withIdent(
                                        NorskIdent()
                                                .withIdent(patientFnr)
                                                .withType(Personidenter().withValue("FNR"))
                                )
                        )
                )
            }

    suspend fun fetchBehandlendeEnhet(tilknytting: GeografiskTilknytning?, patientDiskresjonsKode: String?): FinnBehandlendeEnhetListeResponse? =
            retry(callName = "finn_nav_kontor",
                    retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                    legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
                arbeidsfordelingV1.finnBehandlendeEnhetListe(FinnBehandlendeEnhetListeRequest().apply {
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
                })
            }

    suspend fun fetchDiskresjonsKode(pasientFNR: String): String? {
        val diskresjonskodeSomTall: String? = retry(callName = "tps_diskresjonskode",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
            legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            diskresjonskodeV1.hentDiskresjonskode(WSHentDiskresjonskodeRequest().withIdent(pasientFNR)).diskresjonskode
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
