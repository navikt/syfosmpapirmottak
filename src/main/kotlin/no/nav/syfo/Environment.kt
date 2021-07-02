package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmpapirmottak"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val dokJournalfoeringV1Topic: String = getEnvVar("DOK_JOURNALFOERING_V1_TOPIC"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val safV1Url: String = getEnvVar("SAFGRAPHQL_URL"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL"),
    val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL", "http://sak.default/api/v1/saker"),
    val hentDokumentUrl: String = getEnvVar("HENT_DOKUMENT_URL"),
    val clientId: String = getEnvVar("CLIENT_ID"),
    val helsenettproxyId: String = getEnvVar("HELSENETTPROXY_ID"),
    val aadAccessTokenUrl: String = getEnvVar("AADACCESSTOKEN_URL"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL"),
    val regelEndpointURL: String = getEnvVar("PAPIRREGEL_ENDPOINT_URL"),
    val papirregelId: String = getEnvVar("PAPIRREGEL_ID"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "http://kuhr-sar-api.teamkuhr.svc.nais.local"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val sm2013BehandlingsUtfallTopic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val sm2013ManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013SmregistreringTopic: String = getEnvVar("KAFKA_PAPIR_SM_REGISTERING_TOPIC", "privat-syfo-papir-sm-registering"),
    val syfoserviceMqTopic: String = "privat-syfo-syfoservice-mq",
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD")
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

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
