package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.ArbeidsFordelingClient
import no.nav.syfo.client.ArbeidsfordelingRequest
import no.nav.syfo.log
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.LoggingMeta
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import no.nav.tjeneste.pip.egen.ansatt.v1.WSHentErEgenAnsattEllerIFamilieMedEgenAnsattRequest
import no.nav.tjeneste.virksomhet.person.v3.binding.HentGeografiskTilknytningPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentGeografiskTilknytningSikkerhetsbegrensing
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest

class BehandlendeEnhetService(val personV3: PersonV3, val egenAnsattV1: EgenAnsattV1, val arbeidsfordelingClient: ArbeidsFordelingClient) {

    suspend fun getBehanldendeEnhet(pasient: PdlPerson, loggingMeta: LoggingMeta): String? {
        val geografiskTilknytning = getGeografiskTilknytning(pasient, loggingMeta)
        val egenAnsatt = isEgenAnsatt(pasient)
        return getBehandlendeId(pasient, geografiskTilknytning, egenAnsatt, loggingMeta)
    }

    private suspend fun getBehandlendeId(pasient: PdlPerson, geografiskTilknytning: String?, egenAnsatt: Boolean, loggingMeta: LoggingMeta): String {
        val result = arbeidsfordelingClient.finnBehandlendeEnhet(getArbeidsfordelingRequest(pasient, geografiskTilknytning, egenAnsatt))
        if (result?.firstOrNull()?.enhetId == null) {
            log.warn("arbeidsfordeling fant ingen nav-enheter {}", StructuredArguments.fields(loggingMeta))
        }
        return result?.firstOrNull()?.enhetNr
                ?: "0393"
    }

    private fun isEgenAnsatt(pasient: PdlPerson) =
            egenAnsattV1.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(
                    WSHentErEgenAnsattEllerIFamilieMedEgenAnsattRequest().withIdent(pasient.fnr)).isEgenAnsatt

    private fun getGeografiskTilknytning(pasient: PdlPerson, loggingMeta: LoggingMeta): String? {
        return try {
            personV3.hentGeografiskTilknytning(getGeografiskTilknytningRequest(pasient)).geografiskTilknytning.geografiskTilknytning
        } catch (ex: HentGeografiskTilknytningSikkerhetsbegrensing) {
            handleException(loggingMeta)
        } catch (ex: HentGeografiskTilknytningPersonIkkeFunnet) {
            handleException(loggingMeta)
        }
    }

    private fun getArbeidsfordelingRequest(pasient: PdlPerson, geografiskTilknytning: String?, egenAnsatt: Boolean): ArbeidsfordelingRequest {
        return ArbeidsfordelingRequest(
                tema = "SYM",
                behandlingstema = "ANY",
                behandlingstype = "ANY",
                diskresjonskode = getDiskresjonskode(pasient.adressebeskyttelse),
                geografiskOmraade = geografiskTilknytning,
                oppgavetype = "BEH_EL_SYM",
                skjermet = egenAnsatt
        )
    }

    private fun getDiskresjonskode(adressebeskyttelse: String?): String? {
        return when (adressebeskyttelse) {
            "STRENGT_FORTROLIG" -> "SPSF"
            "FORTROLIG" -> "SPSO"
            else -> null
        }
    }

    private fun handleException(loggingMeta: LoggingMeta): Nothing? {
        log.error("Kunne ikke hente person fra personV3 {}", StructuredArguments.fields(loggingMeta))
        return null
    }

    private fun getGeografiskTilknytningRequest(pasient: PdlPerson): HentGeografiskTilknytningRequest? {
        return HentGeografiskTilknytningRequest()
                        .withAktoer(
                                PersonIdent()
                                        .withIdent(
                                                NorskIdent()
                                                        .withIdent(pasient.fnr)
                                                        .withType(Personidenter().withValue("FNR"))))
    }
}
