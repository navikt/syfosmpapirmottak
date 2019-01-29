package no.nav.syfo

import java.nio.file.Path
import java.nio.file.Paths

val vaultApplicationPropertiesPath: Path = Paths.get("/var/run/secrets/nais.io/vault/credentials.json")

data class ApplicationConfig(
    val applicationPort: Int = 8080,
    val applicationThreads: Int = 1,
    val kafkaBootstrapServers: String,
    val mqQueueManagerName: String,
    val mqHostname: String,
    val mqPort: Int,
    val mqChannelName: String,
    val syfosmpapirmottakinputQueueName: String,
    val kafkaSM2013PapirmottakTopic: String = "privat-syfo-smpapir-automatiskBehandling",
    val syfosmpapirmottakBackoutQueueName: String,
    val syfoSmRegelerApiURL: String = "syfosmregler",
    val kafkaSM2013OppgaveGsakTopic: String = "privat-syfo-smpapir-manuellBehandling"

)

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
)
