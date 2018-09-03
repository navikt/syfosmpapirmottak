package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val srvsyfosmpapirmottakUsername: String = getEnvVar("SRVSYFOSMPAPIRMOTTAK_USERNAME"),
    val srvsyfosmpapirmottakPassword: String = getEnvVar("SRVSYFOSMPAPIRMOTTAK__PASSWORD"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val mqQueueManagerName: String = getEnvVar("MQGATEWAY04_NAME"),
    val mqHostname: String = getEnvVar("MQGATEWAY04_HOSTNAME"),
    val mqPort: Int = getEnvVar("MQGATEWAY04_PORT").toInt(),
    val mqGatewayName: String = getEnvVar("MQGATEWAY04_NAME"),
    val mqChannelName: String = getEnvVar("SYFOSMPAPIRMOTTAK"),
    val srvappserverUsername: String = getEnvVar("SRVAPPSERVER_USERNAME", "srvappserver"),
    val srvappserverPassword: String = getEnvVar("SRVAPPSERVER_PASSWORD", ""),
    val syfosmpapirmottakinputQueueName: String = getEnvVar("SYFSMPAPIROMOTTAK_INPUT_QUEUE_QUEUENAME"),
    val kafkaSM2013PapirmottakTopic: String = getEnvVar("KAFKA_SM2013_PAPIR_MOTTAK_TOPIC", "privat-syfosmpapirmottak-sm2013"),
    val syfomottakinputBackoutQueueName: String = getEnvVar("SYFOMOTTAK_BACKOUT_QUEUE_QUEUENAME")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
