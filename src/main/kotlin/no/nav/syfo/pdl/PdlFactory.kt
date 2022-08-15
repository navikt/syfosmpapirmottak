package no.nav.syfo.pdl

import io.ktor.client.HttpClient
import no.nav.syfo.Environment
import no.nav.syfo.application.azuread.v2.AzureAdV2Client
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService

class PdlFactory private constructor() {
    companion object {
        fun getPdlService(
            environment: Environment,
            httpClient: HttpClient,
            azureAdV2Client: AzureAdV2Client,
            pdlScope: String
        ): PdlPersonService {
            return PdlPersonService(getPdlClient(httpClient, environment), azureAdV2Client, pdlScope)
        }
        private fun getPdlClient(httpClient: HttpClient, environment: Environment): PdlClient {
            return PdlClient(
                httpClient,
                environment.pdlGraphqlPath,
                PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText()
            )
        }
    }
}
