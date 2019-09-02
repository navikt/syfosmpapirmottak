package no.nav.syfo

import com.apollographql.apollo.ApolloClient
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.service.BehandlingService
import no.nav.syfo.service.FordelingsOppgaveService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

const val STANDARD_NAV_ENHET = "0393"

fun doReadynessCheck(): Boolean {
    return true
}

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val log: Logger = LoggerFactory.getLogger("nav.syfo.papirmottak")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val credentials =
        objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())

    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(env, credentials).envOverrides()

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
            "${env.applicationName}-consumer",
            valueDeserializer = KafkaAvroDeserializer::class
    )

    val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
    val apolloClient: ApolloClient = ApolloClient.builder()
        .serverUrl(env.safV1Url)
        .build()
    val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient, httpClient)
    val safJournalpostClient = SafJournalpostClient(apolloClient, oidcClient)
    val safDokumentClient = SafDokumentClient(env.hentDokumentUrl, oidcClient, httpClient)
    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient, httpClient)
    val sakClient = SakClient(env.opprettSakUrl, oidcClient, httpClient)

    val arbeidsfordelingV1 = createPort<ArbeidsfordelingV1>(env.arbeidsfordelingV1EndpointURL) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
    }

    val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
    }

    val diskresjonskodeV1 = createPort<DiskresjonskodePortType>(env.diskresjonskodeEndpointUrl) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
    }

    val oppgaveService = OppgaveService(oppgaveClient, personV3, diskresjonskodeV1, arbeidsfordelingV1)
    val fordelingsOppgaveService = FordelingsOppgaveService(oppgaveService)
    val behandlingService = BehandlingService(safJournalpostClient, aktoerIdClient, sakClient, oppgaveService, fordelingsOppgaveService)

    launchListeners(
            env,
            applicationState,
            consumerProperties,
            behandlingService
    )
    applicationState.initialized = true

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch {
            try {
                action()
            } catch (e: TrackableException) {
                log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", StructuredArguments.fields(e.loggingMeta), e.cause)
            } finally {
                applicationState.running = false
            }
        }

@KtorExperimentalAPI
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    behandlingService: BehandlingService
) {
    val kafkaconsumerJournalfoeringHendelse = KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerProperties)
    kafkaconsumerJournalfoeringHendelse.subscribe(listOf(env.dokJournalfoeringV1Topic))

    createListener(applicationState) {
        blockingApplicationLogic(applicationState, kafkaconsumerJournalfoeringHendelse, behandlingService)
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    consumer: KafkaConsumer<String, JournalfoeringHendelseRecord>,
    behandlingService: BehandlingService
) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val journalfoeringHendelseRecord = consumerRecord.value()
            val sykmeldingId = UUID.randomUUID().toString()
            val loggingMeta = LoggingMeta(
                    sykmeldingId = sykmeldingId,
                    journalpostId = journalfoeringHendelseRecord.journalpostId.toString(),
                    hendelsesId = journalfoeringHendelseRecord.hendelsesId
            )

            behandlingService.handleJournalpost(journalfoeringHendelseRecord, loggingMeta, sykmeldingId)
        }
        delay(100)
    }
}

inline fun <reified T> XMLEIFellesformat.get(): T = any.find { it is T } as T

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(readynessCheck = ::doReadynessCheck, livenessCheck = { applicationState.running })
    }
}
