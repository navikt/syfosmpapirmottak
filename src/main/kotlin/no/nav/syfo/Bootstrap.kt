package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
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
import no.nav.syfo.api.JournalfoerInngaaendeV1Client
import no.nav.syfo.api.StsOidcClient
import no.nav.syfo.api.SyfoSykemelginReglerClient
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.util.readConsumerConfig
import no.nav.syfo.util.readProducerConfig
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringWriter
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.bind.Marshaller

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
                val syfoSykemelginReglerClient = SyfoSykemelginReglerClient(credentials)
                val consumerProperties = readConsumerConfig(config, credentials)
                val kafkaconsumer = KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerProperties)
                kafkaconsumer.subscribe(listOf(config.dokJournalfoeringV1))
                val producerProperties = readProducerConfig(config, credentials, valueSerializer = StringSerializer::class)
                val kafkaproducer = KafkaProducer<String, String>(producerProperties)

                val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
                val journalfoerInngaaendeV1Client = JournalfoerInngaaendeV1Client(config.journalfoerInngaaendeV1URL, oidcClient)

                blockingApplicationLogic(applicationState, kafkaproducer, kafkaconsumer, config, syfoSykemelginReglerClient, journalfoerInngaaendeV1Client)
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

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    producer: KafkaProducer<String, String>,
    consumer: KafkaConsumer<String, JournalfoeringHendelseRecord>,
    config: ApplicationConfig,
    syfoSykemelginReglerClient: SyfoSykemelginReglerClient,
    journalfoerInngaaendeV1Client: JournalfoerInngaaendeV1Client
) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {

            val journalfoeringHendelseRecord = it.value()

            // TODO find a better metod of filter from the kafa topic, only get the right "behandlingstema" and "mottaksKanal"
            if (journalfoeringHendelseRecord.temaNytt == "SYM" &&
                    journalfoeringHendelseRecord.mottaksKanal == "skanning") {
                val logValues = arrayOf(
                        keyValue("smId", "missing"),
                        keyValue("msgId", "missing"),
                        keyValue("orgNr", "missing"),
                        keyValue("journalpostId", journalfoeringHendelseRecord.journalpostId),
                        keyValue("hendelsesId", journalfoeringHendelseRecord.hendelsesId)
                )

                val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }
                log.info("Received a papir SM, $logKeys", *logValues)

                log.info(journalfoeringHendelseRecord.toString())
            }
                // TODO Remove after we get the SYM tema
            else if (journalfoeringHendelseRecord.temaNytt == "SYK") {
                    // TODO Remove after we get the SYM tema
                        // TODO call JOARK, with the journalpostid from the kafa topic
                        log.info("Incoming JoarkHendelse, tema SYK")
                        val journalpost = journalfoerInngaaendeV1Client.getJournalpostMetadata(journalfoeringHendelseRecord.journalpostId)

                        log.info("First document tittle, ${journalpost.dokumentListe.first().tittel}")
                        // TODO get the 3 attachments on that spesific journalpost , xml/ocr, pdf, metadata
                        // journalpost
                        // TODO map the xml file to the healthInformation format
                } else {
                log.info("Incoming JoarkHendelse, NOT papir SM ")
                log.info(journalfoeringHendelseRecord.toString())
            }

            /*
            val validationResult = syfoSykemelginReglerClient.executeRuleValidation(config, text)
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
            */
        }
        delay(100)
    }
}

inline fun <reified T> XMLEIFellesformat.get(): T = any.find { it is T } as T

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

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
