package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmpapirmottak"),
    val dokJournalfoeringAivenTopic: String = getEnvVar("DOK_JOURNALFOERING_AIVEN_TOPIC"),
    val schemaRegistryUrl: String = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
    val kafkaSchemaRegistryUsername: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
    val kafkaSchemaRegistryPassword: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
    val safV1Url: String = getEnvVar("SAFGRAPHQL_URL"),
    val safScope: String = getEnvVar("SAF_SCOPE"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL"),
    val hentDokumentUrl: String = getEnvVar("HENT_DOKUMENT_URL"),
    val helsenettproxyScope: String = getEnvVar("HELSENETT_SCOPE"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL"),
    val regelEndpointURL: String = getEnvVar("PAPIRREGEL_ENDPOINT_URL"),
    val syfosmpapirregelScope: String = getEnvVar("SYFOSMPAPIRREGLER_SCOPE"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL"),
    val kuhrSarApiScope: String = getEnvVar("KUHR_SAR_API_SCOPE"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val dokArkivScope: String = getEnvVar("DOK_ARKIV_SCOPE"),
    val okSykmeldingTopic: String = "teamsykmelding.ok-sykmelding",
    val smregistreringTopic: String = getEnvVar("KAFKA_PAPIR_SM_REGISTERING_TOPIC", "teamsykmelding.papir-sm-registering"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val oppgaveScope: String = getEnvVar("OPPGAVE_SCOPE")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
