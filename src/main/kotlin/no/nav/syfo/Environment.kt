package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.mq.MqConfig

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmpapirmottak"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val dokJournalfoeringV1Topic: String = getEnvVar("DOK_JOURNALFOERING_V1_TOPIC"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val safV1Url: String = getEnvVar("SAFGRAPHQL_URL"),
    val aktoerregisterV1Url: String = getEnvVar("AKTORREGISTER_V1_URL"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL", "http://oppgave/api/v1/oppgaver"),
    val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL", "http://sak/api/v1/saker"),
    val hentDokumentUrl: String = getEnvVar("HENT_DOKUMENT_URL"),
    val clientId: String = getEnvVar("CLIENT_ID"),
    val helsenettproxyId: String = getEnvVar("HELSENETTPROXY_ID"),
    val aadAccessTokenUrl: String = getEnvVar("AADACCESSTOKEN_URL"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL", "http://syfohelsenettproxy"),
    val regelEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL", "http://syfosmpapirregler"),
    val papirregelId: String = getEnvVar("PAPIRREGEL_ID"),
    val syfoserviceQueueName: String = getEnvVar("MQ_SYFOSERVICE_QUEUE_NAME"),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "http://kuhr-sar-api"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val sm2013BehandlingsUtfallTopic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val sm2013ManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013SmregistreringTopic: String = getEnvVar("KAFKA_PAPIR_SM_REGISTERING_TOPIC", "privat-syfo-papir-sm-registering"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH")
) : KafkaConfig, MqConfig

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val clientsecret: String,
    val mqUsername: String,
    val mqPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
