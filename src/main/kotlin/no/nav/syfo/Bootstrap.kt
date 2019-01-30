package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.api.Status
import no.nav.syfo.api.createHttpClient
import no.nav.syfo.api.executeRuleValidation
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.util.readConsumerConfig
import no.nav.syfo.util.readProducerConfig
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

private val log = LoggerFactory.getLogger("nav.syfo.papirmottak")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

@KtorExperimentalAPI
fun main(args: Array<String>) = runBlocking(Executors.newFixedThreadPool(2).asCoroutineDispatcher()) {
    val config: ApplicationConfig = objectMapper.readValue(File(System.getenv("CONFIG_FILE")))
    val credentials: VaultCredentials = objectMapper.readValue(vaultApplicationPropertiesPath.toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, config.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val listeners = (1..config.applicationThreads).map {
            launch {
                val httpClient = createHttpClient(credentials)
                // TODO fix the valueDeserializer, is avro schema, find it could be no.nav.dok:dok-journalfoering-hendelse-v1
                val consumerProperties = readConsumerConfig(config, credentials)
                val kafkaconsumer = KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerProperties)
                kafkaconsumer.subscribe(listOf(config.dokJournalfoeringV1))
                val producerProperties = readProducerConfig(config, credentials, valueSerializer = StringSerializer::class)
                val kafkaproducer = KafkaProducer<String, String>(producerProperties)

                blockingApplicationLogic(applicationState, kafkaproducer, kafkaconsumer, config, httpClient)
            }
        }.toList()

        applicationState.initialized = true
        Runtime.getRuntime().addShutdownHook(Thread {
            applicationServer.stop(10, 10, TimeUnit.SECONDS)
        })
        runBlocking { listeners.forEach { it.join() } }
    } finally {
        applicationState.running = false
    }
}

suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    producer: KafkaProducer<String, String>,
    consumer: KafkaConsumer<String, JournalfoeringHendelseRecord>,
    config: ApplicationConfig,
    httpClient: HttpClient
) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            // TODO get the journalpostid, from the kafa topic, and rember to only take out papir sykmeldinger
            val journalfoeringHendelseRecord = it.value()

            val logValues = arrayOf(
                    keyValue("smId", ""),
                    keyValue("msgId", ""),
                    keyValue("orgNr", "")
            )

            val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }
            log.info("Received a SM2013, $logKeys", *logValues)

            // TODO: use journalpostId
            val smId = UUID.randomUUID().toString()

            val text = ""

            val validationResult = httpClient.executeRuleValidation(config, text)
            when {
                validationResult.status == Status.OK -> {
                    log.info("Rule ValidationResult = OK, $logKeys", *logValues)
                    producer.send(ProducerRecord(config.kafkaSM2013PapirmottakTopic, text))
                }
                validationResult.status == Status.MANUAL_PROCESSING -> {
                    log.info("Rule ValidationResult = MAN $logKeys", *logValues)
                    producer.send(ProducerRecord(config.kafkaSM2013OppgaveGsakTopic, text))
                }
                validationResult.status == Status.INVALID -> {
                    log.error("Rule validation is Invaldid $logKeys", logValues)
                }
            }
        }
        delay(100)
    }
}

inline fun <reified T> XMLEIFellesformat.get(): T = any.find { it is T } as T

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = {
                    applicationState.initialized
                },
                livenessCheck = {
                    applicationState.running
                }
        )
    }
}
