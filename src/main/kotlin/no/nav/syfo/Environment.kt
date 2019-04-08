package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmpapirmottak"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val kafkaSM2013PapirmottakTopic: String = getEnvVar("KAFKA_SMPAPIR_AUTOMATIC_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val sm2013BehandlingsUtfallToipic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val sm2013OppgaveTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_TOPIC", "aapen-syfo-oppgave-produserOppgave"),
    val personV3EndpointURL: String = getEnvVar("PERSON_V3_ENDPOINT_URL"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val arbeidsfordelingV1EndpointURL: String = getEnvVar("ARBEIDSFORDELING_V1_ENDPOINT_URL"),
    val syfoSmRegelerApiURL: String = getEnvVar("SYFO_SM_REGLER_API_URL", "http://syfosmregler"),
    val dokJournalfoeringV1Topic: String = getEnvVar("DOK_JOURNALFOERING_V1", "aapen-dok-journalfoering-v1-q1"),
    val journalfoerInngaaendeV1URL: String = getEnvVar("JOURNAL_FOER_INNGAAENDE_V1_URL"),
    val safURL: String = getEnvVar("SAF_URL"),
    val stsURL: String = getEnvVar("STS_URL"),
    val aktoerregisterV1Url: String = getEnvVar("AKTORREGISTER_V1_URL")

) : KafkaConfig

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
