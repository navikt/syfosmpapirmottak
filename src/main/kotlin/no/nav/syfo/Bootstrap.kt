package no.nav.syfo

import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import no.nav.syfo.api.Status
import no.nav.syfo.api.createHttpClient
import no.nav.syfo.api.executeRuleValidation
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.util.initMqConnection
import no.nav.syfo.util.numListeners
import no.nav.syfo.util.readProducerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.jms.Session
import javax.jms.TextMessage

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

private val log = LoggerFactory.getLogger("nav.syfo.papirmottak")

fun main(args: Array<String>) {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val listeners = (1..env.applicationThreads).map {
            launch {
                val producerProperties = readProducerConfig(env, valueSerializer = StringSerializer::class)
                val kafkaproducer = KafkaProducer<String, String>(producerProperties)
                val httpClient = createHttpClient(env)

                blockingApplicationLogic(applicationState, kafkaproducer, env, httpClient)
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

suspend fun blockingApplicationLogic(applicationState: ApplicationState, producer: KafkaProducer<String, String>, env: Environment, httpClient: HttpClient) {
    while (applicationState.running) {
        initMqConnection(env).use {
            val session = it.createSession(false, Session.AUTO_ACKNOWLEDGE)
            val inputQueue = session.createQueue(env.syfosmpapirmottakinputQueueName)
            val backoutQueue = session.createQueue(env.syfosmpapirmottakBackoutQueueName)
            val backoutProducer = session.createProducer(backoutQueue)

            val listeners = (1..numListeners).map {
                launch {
                    val messageConsumer = session.createConsumer(inputQueue)
                    try {
                        while (applicationState.running) {
                            val message = messageConsumer.receive()
                            if (message == null) {
                                delay(100)
                                continue
                            }
                            try {
                                val inputMessageText = when (message) {
                                    is TextMessage -> message.text
                                    else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
                                }
                                val validationResult = httpClient.executeRuleValidation(env, inputMessageText)
                                when {
                                    validationResult.status == Status.OK -> {
                                        log.info("Rule ValidationResult = OK")
                                        producer.send(ProducerRecord(env.kafkaSM2013PapirmottakTopic, inputMessageText))
                                    }
                                    validationResult.status == Status.MANUAL_PROCESSING -> {
                                        log.info("Rule ValidationResult = MAN")
                                        producer.send(ProducerRecord(env.kafkaSM2013OppgaveGsakTopic, inputMessageText))
                                    }
                                    validationResult.status == Status.INVALID -> {
                                        log.error("Rule validation is Invaldid sending to backout")
                                        backoutProducer.send(message)
                                    }
                                }
                            } catch (e: Exception) {
                                log.error("Exception caught while handling message, sending to backout")
                                backoutProducer.send(message)
                            }
                        }
                    } finally {
                        // Make sure we handle exiting the loop
                        applicationState.running = false
                    }
                }
            }.toList()

            runBlocking {
                listeners.forEach { it.join() }
            }
        }
    }
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
