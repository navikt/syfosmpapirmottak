package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.model.Ident
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PasientLege
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class PdlPersonService(private val pdlClient: PdlClient, private val stsOidcClient: StsOidcClient) {

    suspend fun getPasientOgLege(pasientIdent: String, legeIdent: String, loggingMeta: LoggingMeta) : PasientLege? {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlPasientOgBehandlerResponse = pdlClient.getPasientOgBehandler(pasientIdent, legeIdent, stsToken)

        if(pdlPasientOgBehandlerResponse.errors != null) {
            pdlPasientOgBehandlerResponse.errors.forEach {
                log.error("PDL returnerte error {}, {}", it, StructuredArguments.fields(loggingMeta))
            }
        }

        if(pdlPasientOgBehandlerResponse.data.pasient == null) {
            log.error("Fant ikke pasient i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }
        if(pdlPasientOgBehandlerResponse.data.pasient.navn.isNullOrEmpty()) {
            log.error("Fant ikke navn på person i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }
        if(pdlPasientOgBehandlerResponse.data.pasientIdenter.isNullOrEmpty()) {
            log.error("Fant ikke identer i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }
        if(pdlPasientOgBehandlerResponse.data.lege == null) {
            log.error("Fant ikke lege i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }
        if(pdlPasientOgBehandlerResponse.data.lege.navn.isNullOrEmpty()) {
            log.error("Fant ikke navn på lege i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }
        if(pdlPasientOgBehandlerResponse.data.legeIdenter == null) {
            log.error("Fant ikke legens identer i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }
        return PasientLege(
                pasient = PdlPerson(
                        navn = getNavn(pdlPasientOgBehandlerResponse.data.pasient.navn[0]),
                        aktorId = pdlPasientOgBehandlerResponse.data.pasientIdenter.first { it.gruppe == AKTORID }.ident,
                        fnr = pdlPasientOgBehandlerResponse.data.pasientIdenter.first { it.gruppe == FOLKEREGISTERIDENT }.ident
                ),
                lege = PdlPerson(
                        navn = getNavn(pdlPasientOgBehandlerResponse.data.lege.navn[0]),
                        aktorId = pdlPasientOgBehandlerResponse.data.legeIdenter.first { it.gruppe == AKTORID }.ident,
                        fnr = pdlPasientOgBehandlerResponse.data.legeIdenter.first { it.gruppe == FOLKEREGISTERIDENT }.ident
                )
        )
    }

    suspend fun getPersonnavn(ident: String, loggingMeta: LoggingMeta): PdlPerson? {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlResponse = pdlClient.getPerson(ident, stsToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL returnerte error {}, {}", it, StructuredArguments.fields(loggingMeta))
            }
        }

        if (pdlResponse.data.hentPerson == null) {
            log.error("Fant ikke person i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }
        if (pdlResponse.data.hentPerson.navn.isNullOrEmpty()) {
            log.error("Fant ikke navn på person i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }

        if(pdlResponse.data.hentIdenter.isNullOrEmpty()) {
            log.error("Fant ikke identer i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }

        return PdlPerson(
                navn = getNavn(pdlResponse.data.hentPerson.navn[0]),
                aktorId = pdlResponse.data.hentIdenter.first { it.gruppe == AKTORID }.ident,
                fnr = pdlResponse.data.hentIdenter.first { it.gruppe == FOLKEREGISTERIDENT }.ident
                )
    }

    private fun getNavn(navn: no.nav.syfo.pdl.client.model.Navn): Navn {
        return Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
    }

    companion object {
        private const val FOLKEREGISTERIDENT = "FOLKEREGISTERIDENT"
        private const val AKTORID = "AKTORID"
        private const val FNR = "FNR"
        private const val UNKNOWN = "UNKNOWN"
    }
}
