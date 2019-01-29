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
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
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
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

private val log = LoggerFactory.getLogger("nav.syfo.papirmottak")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

fun main(args: Array<String>) {
    val config: ApplicationConfig = objectMapper.readValue(File(System.getenv("CONFIG_FILE")))
    val credentials: VaultCredentials = objectMapper.readValue(vaultApplicationPropertiesPath.toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, config.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val httpClient = createHttpClient(config, credentials)

        val consumerProperties = readConsumerConfig(config, credentials, valueDeserializer = StringDeserializer::class)
        val producerProperties = readProducerConfig(config, credentials, valueSerializer = StringSerializer::class)
        val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
        kafkaconsumer.subscribe(listOf(config.dokJournalfoeringV1))

        val listeners = (1..config.applicationThreads).map {
            launch {
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
    consumer: KafkaConsumer<String, String>,
    config: ApplicationConfig,
    httpClient: HttpClient
) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            val journarlpost: String = objectMapper.readValue(it.value())

            val logValues = arrayOf(
                    keyValue("smId", ""),
                    keyValue("msgId", ""),
                    keyValue("orgNr", "")
            )

            val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }
            log.info("Received a SM2013, going through rules and persisting in infotrygd $logKeys", *logValues)

            // TODO get the journalpostid, from the kafa topic, and rember to only take out papir sykmeldinger

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
