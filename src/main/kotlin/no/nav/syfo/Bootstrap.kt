package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.getunleash.UnleashContext
import io.getunleash.util.UnleashConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.routing.routing
import io.prometheus.client.hotspot.DefaultExports
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.azure.v2.AzureAdV2Client
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.NyRegelClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.getIcpc2Bdiagnoser
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.aiven.toConsumerConfig
import no.nav.syfo.kafka.aiven.toProducerConfig
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.opprettsykmelding.startOpprettSykmeldingConsumer
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.service.BehandlingService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.service.SykmeldingService
import no.nav.syfo.unleash.Unleash
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.utland.DigitaliseringsoppgaveKafka
import no.nav.syfo.utland.SykDigProducer
import no.nav.syfo.utland.UtenlandskSykmeldingService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("nav.syfo.papirmottak")
val securelog: Logger = LoggerFactory.getLogger("securelog")

val objectMapper: ObjectMapper =
    ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

@DelicateCoroutinesApi
fun main() {
    createApplicationEngine().start(wait = true)
}

fun Application.module() {
    val env = Environment()
    val applicationState = ApplicationState()
    routing { registerNaisApi(applicationState) }

    DefaultExports.initialize()

    val consumerPropertiesAiven =
        KafkaUtils.getAivenKafkaConfig("journalforing-consumer")
            .apply {
                setProperty(
                    KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                    env.schemaRegistryUrl,
                )
                setProperty(
                    KafkaAvroSerializerConfig.USER_INFO_CONFIG,
                    "${env.kafkaSchemaRegistryUsername}:${env.kafkaSchemaRegistryPassword}",
                )
                setProperty(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            }
            .toConsumerConfig(
                "${env.applicationName}-consumer",
                valueDeserializer = KafkaAvroDeserializer::class,
            )
            .also {
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest"
                it["specific.avro.reader"] = true
            }

    val producerPropertiesAiven =
        KafkaUtils.getAivenKafkaConfig("syfosmpapirmottak-producer")
            .toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

    val kafkaProducerReceivedSykmelding =
        KafkaProducer<String, ReceivedSykmeldingWithValidation>(producerPropertiesAiven)
    val kafkaProducerPapirSmRegistering =
        KafkaProducer<String, PapirSmRegistering>(producerPropertiesAiven)
    val sykDigProducer =
        SykDigProducer(
            KafkaProducer<String, DigitaliseringsoppgaveKafka>(producerPropertiesAiven),
            env.sykDigTopic,
        )

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
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
                }
            }
        }
    }
    val retryConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config().apply {
            install(HttpRequestRetry) {
                constantDelay(50, 0, false)
                retryOnExceptionIf(3) { request, throwable ->
                    securelog.warn("Caught exception ${throwable.message}, for url ${request.url}")
                    true
                }
                retryIf(maxRetries) { request, response ->
                    if (response.status.value.let { it in 500..599 }) {
                        securelog.warn(
                            "Retrying for statuscode ${response.status.value}, for url ${request.url}",
                        )
                        true
                    } else {
                        false
                    }
                }
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 20_000
            }
        }
    }

    val httpClient = HttpClient(Apache, retryConfig)

    val azureAdV2Client = AzureAdV2Client(env, httpClient)

    val safJournalpostClient =
        SafJournalpostClient(httpClient, "${env.safV1Url}/graphql", azureAdV2Client, env.safScope)
    val safDokumentClient =
        SafDokumentClient(env.safV1Url, azureAdV2Client, env.safScope, httpClient)
    val oppgaveClient =
        OppgaveClient(
            env.oppgavebehandlingUrl,
            azureAdV2Client,
            httpClient,
            env.oppgaveScope,
            env.cluster,
        )

    val smtssClient = SmtssClient(env.smtssApiUrl, azureAdV2Client, env.smtssApiScope, httpClient)
    val dokArkivClient =
        DokArkivClient(env.dokArkivUrl, azureAdV2Client, env.dokArkivScope, httpClient)

    val oppgaveService = OppgaveService(oppgaveClient)
    val norskHelsenettClient =
        NorskHelsenettClient(
            env.norskHelsenettEndpointURL,
            azureAdV2Client,
            env.helsenettproxyScope,
            httpClient,
        )
    val nyRegelClient = NyRegelClient(azureAdV2Client, env.syfosmreglerScope, httpClient)
    val pdlPersonService = PdlFactory.getPdlService(env, httpClient, azureAdV2Client, env.pdlScope)

    val sykmeldingService =
        SykmeldingService(
            oppgaveService = oppgaveService,
            safDokumentClient = safDokumentClient,
            norskHelsenettClient = norskHelsenettClient,
            nyRegelClient = nyRegelClient,
            smtssClient = smtssClient,
            pdlPersonService = pdlPersonService,
            okSykmeldingTopic = env.okSykmeldingTopic,
            kafkaReceivedSykmeldingProducer = kafkaProducerReceivedSykmelding,
            dokArkivClient = dokArkivClient,
            kafkaproducerPapirSmRegistering = kafkaProducerPapirSmRegistering,
            smregistreringTopic = env.smregistreringTopic,
            icpc2BDiagnoserDeffered = getIcpc2Bdiagnoser(this),
        )

    val unleashContext = UnleashContext.builder().environment(env.unleashEnvironment).build()
    val unleash =
        Unleash(
            unleashConfig =
                UnleashConfig.builder()
                    .appName(env.applicationName)
                    .apiKey(env.unleashApiKey)
                    .instanceId(env.instanceId)
                    .projectName(env.unleashProjectName)
                    .unleashAPI(env.unleashApi)
                    .build(),
            unleashContext,
        )
    val utenlandskSykmeldingService =
        UtenlandskSykmeldingService(oppgaveService, sykDigProducer, env.cluster, unleash)

    val behandlingService =
        BehandlingService(
            safJournalpostClient,
            sykmeldingService,
            utenlandskSykmeldingService,
            pdlPersonService,
        )

    launchListeners(
        env,
        applicationState,
        consumerPropertiesAiven,
        behandlingService,
    )

    startOpprettSykmeldingConsumer(
        env,
        applicationState,
        sykmeldingService,
        safJournalpostClient,
        pdlPersonService,
    )

    monitor.subscribe(ApplicationStopping) {
        applicationState.ready = false
        applicationState.alive = false
        httpClient.close()
    }
}

fun Application.createListener(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit
): Job =
    launch(Dispatchers.IO) {
        try {
            action()
        } catch (e: Exception) {
            log.error(
                "En uhåndtert feil oppstod, applikasjonen restarter",
                e,
            )
        } finally {
            applicationState.ready = false
            applicationState.alive = false
        }
    }

fun Application.launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerPropertiesAiven: Properties,
    behandlingService: BehandlingService,
) {
    val kafkaConsumerJournalfoeringHendelseAiven =
        KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerPropertiesAiven)
    kafkaConsumerJournalfoeringHendelseAiven.subscribe(listOf(env.dokJournalfoeringAivenTopic))

    createListener(applicationState) {
        blockingApplicationLogic(
            applicationState = applicationState,
            aivenConsumer = kafkaConsumerJournalfoeringHendelseAiven,
            behandlingService = behandlingService,
            env = env,
        )
    }
}

suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    aivenConsumer: KafkaConsumer<String, JournalfoeringHendelseRecord>,
    behandlingService: BehandlingService,
    env: Environment,
) {
    while (applicationState.ready) {
        aivenConsumer.poll(Duration.ofMillis(1000)).forEach { consumerRecord ->
            try {
                val journalfoeringHendelseRecord = consumerRecord.value()
                val sykmeldingId = UUID.randomUUID().toString()
                val loggingMeta =
                    LoggingMeta(
                        sykmeldingId = sykmeldingId,
                        journalpostId = journalfoeringHendelseRecord.journalpostId.toString(),
                        hendelsesId = journalfoeringHendelseRecord.hendelsesId,
                    )

                behandlingService.handleJournalpost(
                    journalfoeringEvent = journalfoeringHendelseRecord,
                    loggingMeta = loggingMeta,
                    sykmeldingId = sykmeldingId,
                )
            } catch (e: Exception) {
                if (env.cluster != "dev-gcp") {
                    throw e
                } else {
                    log.warn("Skipping error in dev", e)
                }
            }
        }
    }
}
