package no.nav.syfo.pdl.client

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import no.nav.syfo.pdl.client.model.GetPasientOgBehandler
import no.nav.syfo.pdl.client.model.GetPasientOgBehandlerVariables
import no.nav.syfo.pdl.client.model.GetPasientOgLegeRequest
import no.nav.syfo.pdl.client.model.GetPasientOgLegeResponse
import no.nav.syfo.pdl.client.model.GetPersonRequest
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.GetPersonVariables

class PdlClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val graphQlQuery: String,
    private val graphQlPasientOgBehandlerQuery: String
) {
    private val navConsumerToken = "Nav-Consumer-Token"
    private val temaHeader = "TEMA"
    private val tema = "SYM"

    suspend fun getPerson(fnr: String, stsToken: String): GetPersonResponse {
        val getPersonRequest = GetPersonRequest(query = graphQlQuery, variables = GetPersonVariables(ident = fnr))
        return getGraphQLRespnse(getPersonRequest, stsToken)
    }

    suspend fun getPasientOgBehandler(pasientIdent: String, legeIdent: String, stsToken: String): GetPasientOgLegeResponse {
        val getPasientOgLegeRequest = GetPasientOgLegeRequest(query = graphQlPasientOgBehandlerQuery, variables = GetPasientOgBehandlerVariables(ident = pasientIdent, legeIdent = legeIdent))
        return getGraphQLRespnse(getPasientOgLegeRequest, stsToken)
    }

    private suspend inline fun <reified R> getGraphQLRespnse(graphQlBody: Any, stsToken: String) : R {
        return httpClient.post(basePath) {
            body = graphQlBody
            header(HttpHeaders.Authorization, "Bearer $stsToken")
            header(temaHeader, tema)
            header(HttpHeaders.ContentType, "application/json")
            header(navConsumerToken, "Bearer $stsToken")
        }
    }
}
