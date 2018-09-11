package no.nav.syfo

import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.syfo.api.Status
import no.nav.syfo.api.createHttpClient
import no.nav.syfo.api.executeRuleValidation
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.util.initMqConnection
import no.nav.syfo.util.readProducerConfig
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.io.StringWriter
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.jms.Connection
import javax.jms.Queue
import javax.jms.Session
import javax.jms.TextMessage
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

private val log = LoggerFactory.getLogger("nav.syfo.papirmottak")
val jaxbContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLMsgHead::class.java,
        XMLMottakenhetBlokk::class.java)
val marshaller: Marshaller = jaxbContext.createMarshaller()
val unmarshaller: Unmarshaller = jaxbContext.createUnmarshaller()

fun main(args: Array<String>) {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val httpClient = createHttpClient(env)
        initMqConnection(env).use { connection ->
        connection.start()

        val producerProperties = readProducerConfig(env, valueSerializer = StringSerializer::class)

        val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        val inputQueue = session.createQueue(env.syfosmpapirmottakinputQueueName)
        val backoutQueue = session.createQueue(env.syfosmpapirmottakBackoutQueueName)
        session.close()

        val listeners = (1..env.applicationThreads).map {
            launch {
                val kafkaproducer = KafkaProducer<String, String>(producerProperties)

                blockingApplicationLogic(applicationState, kafkaproducer, env, httpClient, inputQueue, backoutQueue, connection)
            }
        }.toList()

        applicationState.initialized = true
        Runtime.getRuntime().addShutdownHook(Thread {
            applicationServer.stop(10, 10, TimeUnit.SECONDS)
        })
        runBlocking { listeners.forEach { it.join() } }
    }
    } finally {
        applicationState.running = false
    }
}

suspend fun blockingApplicationLogic(applicationState: ApplicationState, producer: KafkaProducer<String, String>, env: Environment, httpClient: HttpClient, inputQueue: Queue, backoutQueue: Queue, connection: Connection) {
    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    val inputConsumer = session.createConsumer(inputQueue)
    val backoutProducer = session.createProducer(backoutQueue)
    while (applicationState.running) {

        val message = inputConsumer.receiveNoWait()
        if (message == null) {
            delay(100)
            continue
        }

        // TODO: use journalpostId
        val smId = UUID.randomUUID().toString()

        val logValues = arrayOf(
                keyValue("smId", smId),
                keyValue("organizationNumber", "TODO"),
                keyValue("msgId", smId)
        )
        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "{}"
        }

        try {
            val inputMessageText = when (message) {
                is TextMessage -> message.text
                else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
            }

            val fellesformat = unmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            fellesformat.get<XMLMottakenhetBlokk>().ediLoggId = smId
            fellesformat.get<XMLMsgHead>().msgInfo.msgId = smId

            val text = StringWriter().use {
                marshaller.marshal(fellesformat, it)
                it.toString()
            }

            val validationResult = httpClient.executeRuleValidation(env, text)
            when {
                validationResult.status == Status.OK -> {
                    log.info("Rule ValidationResult = OK, $logKeys", *logValues)
                    producer.send(ProducerRecord(env.kafkaSM2013PapirmottakTopic, text))
                }
                validationResult.status == Status.MANUAL_PROCESSING -> {
                    log.info("Rule ValidationResult = MAN $logKeys", *logValues)
                    producer.send(ProducerRecord(env.kafkaSM2013OppgaveGsakTopic, text))
                }
                validationResult.status == Status.INVALID -> {
                    log.error("Rule validation is Invaldid sending to backout $logKeys", logValues)
                    backoutProducer.send(message)
                }
            }
        } catch (e: Exception) {
            log.error("Exception caught while handling message, sending to backout $logKeys", e, *logValues)
            backoutProducer.send(message)
        }
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
