package no.nav.syfo

import java.nio.file.Path
import java.nio.file.Paths

val vaultApplicationPropertiesPath: Path = Paths.get("/var/run/secrets/nais.io/vault/credentials.json")

data class ApplicationConfig(
    val applicationPort: Int = 8080,
    val applicationThreads: Int = 1,
    val kafkaBootstrapServers: String,
    val kafkaSM2013PapirmottakTopic: String = "privat-syfo-smpapir-automatiskBehandling",
    val syfoSmRegelerApiURL: String = "syfosmregler",
    val kafkaSM2013OppgaveGsakTopic: String = "privat-syfo-smpapir-manuellBehandling",
    val dokJournalfoeringV1: String,
    val journalfoerInngaaendeV1URL: String

)

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String
)
