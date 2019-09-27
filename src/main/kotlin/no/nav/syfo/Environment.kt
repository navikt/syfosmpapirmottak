package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmpapirmottak"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val dokJournalfoeringV1Topic: String = getEnvVar("DOK_JOURNALFOERING_V1_TOPIC"),
    val personV3EndpointURL: String = getEnvVar("PERSON_V3_ENDPOINT_URL"),
    val diskresjonskodeEndpointUrl: String = getEnvVar("DISKRESJONSKODE_ENDPOINT_URL"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val arbeidsfordelingV1EndpointURL: String = getEnvVar("ARBEIDSFORDELING_V1_ENDPOINT_URL"),
    val safV1Url: String = getEnvVar("SAFGRAPHQL_URL"),
    val aktoerregisterV1Url: String = getEnvVar("AKTORREGISTER_V1_URL"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL", "http://oppgave/api/v1/oppgaver"),
    val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL", "http://sak/api/v1/saker"),
    val hentDokumentUrl: String = getEnvVar("HENT_DOKUMENT_URL"),
    val clientId: String = getEnvVar("CLIENT_ID"),
    val aadAccessTokenUrl: String = getEnvVar("AADACCESSTOKEN_URL")
) : KafkaConfig

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val clientsecret: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
