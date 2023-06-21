package no.nav.syfo.pdl.service

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.LoggingMeta

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClientV2: AzureAdV2Client,
    private val pdlScope: String,
) {

    suspend fun getPdlPerson(ident: String, loggingMeta: LoggingMeta): PdlPerson? {
        val accessToken = accessTokenClientV2.getAccessToken(pdlScope)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for PDL")
        }
        val pdlResponse = pdlClient.getPerson(ident, accessToken.accessToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error(
                    "PDL returnerte error {}, {}",
                    it,
                    StructuredArguments.fields(loggingMeta)
                )
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

        if (
            pdlResponse.data.hentIdenter == null ||
                pdlResponse.data.hentIdenter.identer.isNullOrEmpty()
        ) {
            log.error("Fant ikke identer i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }

        return PdlPerson(
            navn = getNavn(pdlResponse.data.hentPerson.navn[0]),
            aktorId =
                pdlResponse.data.hentIdenter.identer.firstOrNull { it.gruppe == AKTORID }?.ident,
            fnr =
                pdlResponse.data.hentIdenter.identer
                    .firstOrNull { it.gruppe == FOLKEREGISTERIDENT }
                    ?.ident,
            adressebeskyttelse =
                pdlResponse.data.hentPerson.adressebeskyttelse?.firstOrNull()?.gradering,
        )
    }

    private fun getNavn(navn: no.nav.syfo.pdl.client.model.Navn): Navn {
        return Navn(
            fornavn = navn.fornavn,
            mellomnavn = navn.mellomnavn,
            etternavn = navn.etternavn
        )
    }

    companion object {
        private const val FOLKEREGISTERIDENT = "FOLKEREGISTERIDENT"
        private const val AKTORID = "AKTORID"
    }
}
