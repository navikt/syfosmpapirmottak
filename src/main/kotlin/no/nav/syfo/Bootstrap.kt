package no.nav.syfo

import com.apollographql.apollo.ApolloClient
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.net.ProxySelector
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties
import java.util.UUID
import javax.jms.MessageProducer
import javax.jms.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.client.AccessTokenClient
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.service.BehandlingService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.service.UtenlandskSykmeldingService
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
    val applicationEngine = createApplicationEngine(
            env,
            applicationState)

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    if (Diagnosekoder.icd10.isEmpty() || Diagnosekoder.icpc2.isEmpty()) {
        throw RuntimeException("Kunne ikke laste ICD10/ICPC2-diagnosekoder.")
    }

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(env, credentials).envOverrides()
    kafkaBaseConfig["auto.offset.reset"] = "latest"

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
            "${env.applicationName}-consumer-v2",
            valueDeserializer = KafkaAvroDeserializer::class
    )

    val producerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

    val kafkaProducerReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)

    val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
    }
    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }
    val httpClientWithProxy = HttpClient(Apache, proxyConfig)
    val httpClient = HttpClient(Apache, config)

    val apolloClient: ApolloClient = ApolloClient.builder()
            .serverUrl(env.safV1Url)
            .build()
    val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient, httpClient)
    val safJournalpostClient = SafJournalpostClient(apolloClient, oidcClient)
    val safDokumentClient = SafDokumentClient(env.hentDokumentUrl, oidcClient, httpClient)
    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient, httpClient)
    val sakClient = SakClient(env.opprettSakUrl, oidcClient, httpClient)
    val kuhrsarClient = SarClient(env.kuhrSarApiUrl, httpClient)
    val dokArkivClient = DokArkivClient(env.dokArkivUrl, oidcClient, httpClient)

    val oppgaveService = OppgaveService(oppgaveClient)
    val accessTokenClient = AccessTokenClient(env.aadAccessTokenUrl, env.clientId, credentials.clientsecret, httpClientWithProxy)
    val norskHelsenettClient = NorskHelsenettClient(env.norskHelsenettEndpointURL, accessTokenClient, env.helsenettproxyId, httpClient)
    val regelClient = RegelClient(env.regelEndpointURL, accessTokenClient, env.papirregelId, httpClient)
    val sykmeldingService = SykmeldingService(sakClient, oppgaveService, safDokumentClient, norskHelsenettClient, aktoerIdClient, regelClient)
    val utenlandskSykmeldingService = UtenlandskSykmeldingService(sakClient, oppgaveService)
    val behandlingService = BehandlingService(safJournalpostClient, aktoerIdClient, sykmeldingService, utenlandskSykmeldingService)

    launchListeners(
            env,
            applicationState,
            consumerProperties,
            behandlingService,
            credentials,
            kafkaProducerReceivedSykmelding,
            kuhrsarClient,
            dokArkivClient
    )
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch {
            try {
                action()
            } catch (e: TrackableException) {
                log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", StructuredArguments.fields(e.loggingMeta), e.cause)
            } finally {
                applicationState.alive = false
            }
        }

@KtorExperimentalAPI
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    behandlingService: BehandlingService,
    credentials: VaultCredentials,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    kuhrSarClient: SarClient,
    dokArkivClient: DokArkivClient
) {
    val kafkaconsumerJournalfoeringHendelse = KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerProperties)
    kafkaconsumerJournalfoeringHendelse.subscribe(listOf(env.dokJournalfoeringV1Topic))

    applicationState.ready = true

    createListener(applicationState) {
        connectionFactory(env).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

            val syfoserviceProducer = session.producerForQueue(env.syfoserviceQueueName)
            blockingApplicationLogic(applicationState, kafkaconsumerJournalfoeringHendelse,
                    behandlingService, syfoserviceProducer, session,
                    env.sm2013AutomaticHandlingTopic, kafkaproducerreceivedSykmelding, kuhrSarClient, dokArkivClient)
        }
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    consumer: KafkaConsumer<String, JournalfoeringHendelseRecord>,
    behandlingService: BehandlingService,
    syfoserviceProducer: MessageProducer,
    session: Session,
    sm2013AutomaticHandlingTopic: String,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    kuhrSarClient: SarClient,
    dokArkivClient: DokArkivClient
) {
    while (applicationState.ready) {
        consumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val journalfoeringHendelseRecord = consumerRecord.value()
            val sykmeldingId = UUID.randomUUID().toString()
            val loggingMeta = LoggingMeta(
                    sykmeldingId = sykmeldingId,
                    journalpostId = journalfoeringHendelseRecord.journalpostId.toString(),
                    hendelsesId = journalfoeringHendelseRecord.hendelsesId
            )

            behandlingService.handleJournalpost(journalfoeringHendelseRecord, loggingMeta,
                    sykmeldingId, syfoserviceProducer, session,
                    sm2013AutomaticHandlingTopic, kafkaproducerreceivedSykmelding, kuhrSarClient, dokArkivClient)
        }
        delay(100)
    }
}
