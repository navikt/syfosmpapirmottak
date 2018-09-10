package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val srvsyfosmpapirmottakUsername: String = getEnvVar("SRVSYFOSMPAPIRMOTTAK_USERNAME"),
    val srvsyfosmpapirmottakPassword: String = getEnvVar("SRVSYFOSMPAPIRMOTTAK_PASSWORD"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val mqQueueManagerName: String = getEnvVar("MQGATEWAY03_NAME"),
    val mqHostname: String = getEnvVar("MQGATEWAY03_HOSTNAME"),
    val mqPort: Int = getEnvVar("MQGATEWAY03_PORT").toInt(),
    val mqChannelName: String = getEnvVar("SYFOSMPAPIRMOTTAK_CHANNEL_NAME"),
    val srvappserverUsername: String = getEnvVar("SRVAPPSERVER_USERNAME", "srvappserver"),
    val srvappserverPassword: String = getEnvVar("SRVAPPSERVER_PASSWORD", ""),
    val syfosmpapirmottakinputQueueName: String = getEnvVar("SYFOSMPAPIRMOTTAK_INPUT_QUEUENAME"),
    val kafkaSM2013PapirmottakTopic: String = getEnvVar("KAFKA_SM2013_PAPIR_MOTTAK_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val syfosmpapirmottakBackoutQueueName: String = getEnvVar("SYFOSMPAPIRMOTTAK_BACKOUT_QUEUENAME"),
    val syfoSmRegelerApiURL: String = getEnvVar("SYFO_SYKEMELDINGREGLER_API_URL", "syfosmregler"),
    val kafkaSM2013OppgaveGsakTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_TOPIC", "privat-syfo-smpapir-manuellBehandling")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
