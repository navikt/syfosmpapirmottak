package no.nav.syfo.pdl.client.model

data class GetPersonRequest(val query: String, val variables: GetPersonVariables)

data class GetPasientOgLegeRequest(val query: String, val variables: GetPasientOgBehandlerVariables)
