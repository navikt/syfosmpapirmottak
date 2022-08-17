package no.nav.syfo

import com.apollographql.apollo.ApolloClient
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.service.BehandlingService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.service.UtenlandskSykmeldingService
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.Properties
import java.util.UUID

val log: Logger = LoggerFactory.getLogger("nav.syfo.papirmottak")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

@DelicateCoroutinesApi
fun main() {
    val env = Environment()

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    if (Diagnosekoder.icd10.isEmpty() || Diagnosekoder.icpc2.isEmpty()) {
        throw RuntimeException("Kunne ikke laste ICD10/ICPC2-diagnosekoder.")
    }

    DefaultExports.initialize()

    val consumerPropertiesAiven = KafkaUtils.getAivenKafkaConfig().apply {
        setProperty(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, env.schemaRegistryUrl)
        setProperty(KafkaAvroSerializerConfig.USER_INFO_CONFIG, "${env.kafkaSchemaRegistryUsername}:${env.kafkaSchemaRegistryPassword}")
        setProperty(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
    }.toConsumerConfig(
        "${env.applicationName}-consumer",
        valueDeserializer = KafkaAvroDeserializer::class
    ).also {
        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        it["specific.avro.reader"] = true
    }

    val producerPropertiesAiven = KafkaUtils.getAivenKafkaConfig()
        .toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

    val kafkaProducerReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerPropertiesAiven)
    val kafkaProducerPapirSmRegistering = KafkaProducer<String, PapirSmRegistering>(producerPropertiesAiven)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
    }
    val retryConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config().apply {
            install(HttpRequestRetry) {
                maxRetries = 3
                delayMillis { retry ->
                    retry * 500L
                }
            }
        }
    }

    val httpClientWithRetry = HttpClient(Apache, retryConfig)
    val httpClient = HttpClient(Apache, config)

    val accessTokenClientV2 = AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClient)

    val apolloClient: ApolloClient = ApolloClient.builder()
        .serverUrl(env.safV1Url)
        .build()
    val safJournalpostClient = SafJournalpostClient(apolloClient, accessTokenClientV2, env.safScope)
    val safDokumentClient = SafDokumentClient(env.hentDokumentUrl, accessTokenClientV2, env.safScope, httpClientWithRetry)
    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, accessTokenClientV2, httpClientWithRetry, env.oppgaveScope)

    val kuhrsarClient = SarClient(env.smgcpProxyUrl, accessTokenClientV2, env.smgcpProxyScope, httpClientWithRetry)
    val dokArkivClient = DokArkivClient(env.dokArkivUrl, accessTokenClientV2, env.dokArkivScope, httpClientWithRetry)

    val oppgaveService = OppgaveService(oppgaveClient)
    val norskHelsenettClient = NorskHelsenettClient(env.norskHelsenettEndpointURL, accessTokenClientV2, env.helsenettproxyScope, httpClientWithRetry)
    val regelClient = RegelClient(env.regelEndpointURL, accessTokenClientV2, env.syfosmpapirregelScope, httpClientWithRetry)
    val pdlPersonService = PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)

    val sykmeldingService = SykmeldingService(
        oppgaveService = oppgaveService,
        safDokumentClient = safDokumentClient,
        norskHelsenettClient = norskHelsenettClient,
        regelClient = regelClient,
        kuhrSarClient = kuhrsarClient,
        pdlPersonService = pdlPersonService,
        okSykmeldingTopic = env.okSykmeldingTopic,
        kafkaReceivedSykmeldingProducer = kafkaProducerReceivedSykmelding,
        dokArkivClient = dokArkivClient,
        kafkaproducerPapirSmRegistering = kafkaProducerPapirSmRegistering,
        smregistreringTopic = env.smregistreringTopic
    )
    val utenlandskSykmeldingService = UtenlandskSykmeldingService(oppgaveService)
    val behandlingService = BehandlingService(safJournalpostClient, sykmeldingService, utenlandskSykmeldingService, pdlPersonService)

    launchListeners(
        env,
        applicationState,
        consumerPropertiesAiven,
        behandlingService
    )

    applicationServer.start()
}

@DelicateCoroutinesApi
fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", StructuredArguments.fields(e.loggingMeta), e.cause)
        } finally {
            applicationState.ready = false
            applicationState.alive = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerPropertiesAiven: Properties,
    behandlingService: BehandlingService
) {
    val kafkaConsumerJournalfoeringHendelseAiven = KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerPropertiesAiven)
    kafkaConsumerJournalfoeringHendelseAiven.subscribe(listOf(env.dokJournalfoeringAivenTopic))

    createListener(applicationState) {

        blockingApplicationLogic(
            applicationState = applicationState,
            aivenConsumer = kafkaConsumerJournalfoeringHendelseAiven,
            behandlingService = behandlingService
        )
    }
}

suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    aivenConsumer: KafkaConsumer<String, JournalfoeringHendelseRecord>,
    behandlingService: BehandlingService
) {
    while (applicationState.ready) {
        aivenConsumer.poll(Duration.ofMillis(1000)).forEach { consumerRecord ->
            val journalfoeringHendelseRecord = consumerRecord.value()
            val sykmeldingId = UUID.randomUUID().toString()
            val loggingMeta = LoggingMeta(
                sykmeldingId = sykmeldingId,
                journalpostId = journalfoeringHendelseRecord.journalpostId.toString(),
                hendelsesId = journalfoeringHendelseRecord.hendelsesId
            )

            behandlingService.handleJournalpost(
                journalfoeringEvent = journalfoeringHendelseRecord,
                loggingMeta = loggingMeta,
                sykmeldingId = sykmeldingId
            )
        }
    }
}
