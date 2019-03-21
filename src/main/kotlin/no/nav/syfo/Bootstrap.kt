package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.paop.ws.configureSTSFor
import no.nav.syfo.client.JournalfoerInngaaendeV1Client
import no.nav.syfo.client.SafClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.client.ValidationResult
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.loadBaseConfig
import no.nav.syfo.util.toConsumerConfig
import no.nav.syfo.util.toProducerConfig
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeResponse
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringWriter
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.bind.Marshaller

fun doReadynessCheck(): Boolean {
    return true
}

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val log = LoggerFactory.getLogger("nav.syfo.papirmottak")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

@KtorExperimentalAPI
fun main(args: Array<String>) = runBlocking(Executors.newFixedThreadPool(4).asCoroutineDispatcher()) {
    val config: ApplicationConfig = objectMapper.readValue(File(System.getenv("CONFIG_FILE")))
    val credentials: VaultCredentials = objectMapper.readValue(vaultApplicationPropertiesPath.toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, config.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

    try {
        val listeners = (1..config.applicationThreads).map {
            launch {
                val syfoSykemelginReglerClient = SyfoSykemeldingRuleClient(config.syfoSmRegelerApiURL, credentials)
                val oidcClient = StsOidcClient(config.stsURL, credentials.serviceuserUsername, credentials.serviceuserPassword)
                val journalfoerInngaaendeV1Client = JournalfoerInngaaendeV1Client(config.journalfoerInngaaendeV1URL, oidcClient)
                val safClient = SafClient(config.safURL, oidcClient)
                val aktoerIdClient = AktoerIdClient(config.aktoerregisterV1Url, oidcClient)

                val arbeidsfordelingV1 = JaxWsProxyFactoryBean().apply {
                    address = config.arbeidsfordelingV1EndpointURL
                    serviceClass = ArbeidsfordelingV1::class.java
                }.create() as ArbeidsfordelingV1
                configureSTSFor(arbeidsfordelingV1, credentials.serviceuserUsername,
                        credentials.serviceuserPassword, config.securityTokenServiceUrl)

                val personV3 = JaxWsProxyFactoryBean().apply {
                    address = config.personV3EndpointURL
                    serviceClass = PersonV3::class.java
                }.create() as PersonV3
                configureSTSFor(personV3, credentials.serviceuserUsername,
                        credentials.serviceuserPassword, config.securityTokenServiceUrl)

                val kafkaBaseConfig = loadBaseConfig(config, credentials)

                val producerProperties = kafkaBaseConfig.toProducerConfig(config.applicationName, valueSerializer = StringSerializer::class)
                val consumerProperties = kafkaBaseConfig.toConsumerConfig("${config.applicationName}-consumer", valueDeserializer = KafkaAvroDeserializer::class)

                val kafkaconsumer = KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerProperties)
                kafkaconsumer.subscribe(listOf(config.dokJournalfoeringV1))

                val kafkaproducer = KafkaProducer<String, String>(producerProperties)

                blockingApplicationLogic(applicationState, kafkaproducer, kafkaconsumer, config, syfoSykemelginReglerClient, journalfoerInngaaendeV1Client, safClient, aktoerIdClient, personV3, arbeidsfordelingV1, credentials)
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
suspend fun CoroutineScope.blockingApplicationLogic(
    applicationState: ApplicationState,
    producer: KafkaProducer<String, String>,
    consumer: KafkaConsumer<String, JournalfoeringHendelseRecord>,
    config: ApplicationConfig,
    syfoSykemelginReglerClient: SyfoSykemeldingRuleClient,
    journalfoerInngaaendeV1Client: JournalfoerInngaaendeV1Client,
    safClient: SafClient,
    aktoerIdClient: AktoerIdClient,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    credentials: VaultCredentials
) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            val journalfoeringHendelseRecord = it.value()

            val logValues = arrayOf(
                    keyValue("smId", "missing"),
                    keyValue("msgId", "missing"),
                    keyValue("orgNr", "missing"),
                    keyValue("journalpostId", journalfoeringHendelseRecord.journalpostId),
                    keyValue("hendelsesId", journalfoeringHendelseRecord.hendelsesId)
            )

            val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }

            try {
                // TODO find a better metod of filter from the kafa topic, only get the right "behandlingstema" and
                // TODO "mottaksKanal"
                if (journalfoeringHendelseRecord.temaNytt.toString() == "SYM" &&
                        journalfoeringHendelseRecord.mottaksKanal == "skanning") {
                    log.info("Received a papir SM, $logKeys", *logValues)
                    log.info("journalfoeringHendelseRecord:", objectMapper.writeValueAsString(journalfoeringHendelseRecord))
                    val journalpost = journalfoerInngaaendeV1Client.getJournalpostMetadata(
                            journalfoeringHendelseRecord.journalpostId,
                            logKeys,
                            logValues).await()

                    val smpapirXMLDokumentInfoId = journalpost.dokumentListe.first {
                        it.variant.first().variantFormat == "XML"
                    }.dokumentId

                    val smpapirPDFDokumentInfoId = journalpost.dokumentListe.first {
                        it.variant.first().variantFormat == "PDF"
                    }.dokumentId

                    val smpapirMetadataDokumentInfoId = journalpost.dokumentListe.first {
                        it.variant.first().variantFormat == "ARKIV"
                    }.dokumentId

                    // TODO get the 3 attachments on that spesific journalpost , xml/ocr, pdf, metadata
                    log.info("Calling saf rest")
                    // TODO, change return type of safClient based on VariantFormat
                    val smpapirMetadata = safClient.getdokument(
                            journalfoeringHendelseRecord.journalpostId,
                            smpapirMetadataDokumentInfoId,
                            "ARKIV",
                            logKeys,
                            logValues).await()

                    val smpapirXML = safClient.getdokument(
                            journalfoeringHendelseRecord.journalpostId,
                            smpapirMetadataDokumentInfoId,
                            "ARKIV",
                            logKeys,
                            logValues).await()

                    val smpapirPDF = safClient.getdokument(
                            journalfoeringHendelseRecord.journalpostId,
                            smpapirMetadataDokumentInfoId,
                            "ARKIV",
                            logKeys,
                            logValues).await()
                }
                // TODO Remove after we get the SYM tema
                else if (journalfoeringHendelseRecord.temaNytt.toString() == "SYK" &&
                        journalfoeringHendelseRecord.hendelsesType == "EndeligJournalført") {
                    log.info("Incoming JoarkHendelse, tema SYK $logKeys", *logValues)

                    log.info(journalfoeringHendelseRecord.toString())
                    val journalpost = journalfoerInngaaendeV1Client.getJournalpostMetadata(
                            journalfoeringHendelseRecord.journalpostId,
                            logKeys,
                            logValues).await()

                    val dokumentInfoId = journalpost.dokumentListe.first {
                        it.variant.first().variantFormat == "ARKIV"
                    }.dokumentId

                    log.info("Found dokumentId: $dokumentInfoId")
                    // TODO get the 3 attachments on that spesific journalpost , xml/ocr, pdf, metadata
                    log.info("Calling saf rest")
                    val paperSickLave = safClient.getdokument(
                            journalfoeringHendelseRecord.journalpostId,
                            dokumentInfoId,
                            "ARKIV",
                            logKeys,
                            logValues).await()
                    }

                    // TODO Unmarshaller docoument from saf to corret type
                    // example: SykemeldingerType
                    // TODO map the xml file to the healthInformation format
                    /* val sykmeldingtype = sykemeldingerTypeUnmarshaller.unmarshal(objectMapper.writeValueAsString(paperSickLave)) as SykemeldingerType
                        sykmeldingtype.toSykmelding(
                        sykmeldingId = UUID.randomUUID().toString(),
                        pasientAktoerId = patientIdents.identer!!.first().ident,
                        legeAktoerId = doctorIdents.identer!!.first().ident,
                        msgId = msgId
                    )*/

                    // TODO
                    /*
                    val aktoerIdsDeferred = aktoerIdClient.getAktoerIds(listOf(personNumberDoctor, personNumberPatient), msgId, credentials.serviceuserUsername)

                    val aktoerIds = aktoerIdsDeferred

                    val patientIdents = aktoerIds[personNumberPatient]
                    val doctorIdents = aktoerIds[personNumberDoctor]

                    if (patientIdents == null || patientIdents.feilmelding != null) {
                        log.info("Patient not found i aktorRegister $logKeys, {}", *logValues,
                                keyValue("errorMessage", patientIdents?.feilmelding ?: "No response for FNR"))
                    }
                    if (doctorIdents == null || doctorIdents.feilmelding != null) {
                        log.info("Doctor not found i aktorRegister $logKeys, {}", *logValues,
                                keyValue("errorMessage", doctorIdents?.feilmelding ?: "No response for FNR"))
                    }


                }

                val validationResult = syfoSykemelginReglerClient.executeRuleValidation(config, text)
                when {
                    validationResult.status == Status.OK -> {
                        log.info("Rule ValidationResult = OK, $logKeys", *logValues)
                        producer.send(ProducerRecord(config.kafkaSM2013PapirmottakTopic, text))
                    }
                    validationResult.status == Status.MANUAL_PROCESSING -> {
                        val geografiskTilknytning = fetchGeografiskTilknytningAsync(personV3, receivedSykmelding)
                        val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhetAsync(arbeidsfordelingV1, geografiskTilknytning.await().geografiskTilknytning)
                        createTask(kafkaManuelTaskProducer, receivedSykmelding, validationResult, findNavOffice(finnBehandlendeEnhetListeResponse.await()), logKeys, logValues)
                    }
                    validationResult.status == Status.INVALID -> {
                        log.error("Rule validation is Invaldid $logKeys", logValues)
                    }
                }
                */
            } catch (e: Exception) {
                log.error("Exception caught while handling message, sending to backout $logKeys", *logValues, e)
            }
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
        registerNaisApi(readynessCheck = ::doReadynessCheck, livenessCheck = { applicationState.running })
    }
}

fun createTask(kafkaProducer: KafkaProducer<String, ProduceTask>, receivedSykmelding: ReceivedSykmelding, results: ValidationResult, navKontor: String, logKeys: String, logValues: Array<StructuredArgument>) {
    kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", receivedSykmelding.msgId, ProduceTask().apply {
        messageId = receivedSykmelding.msgId
        aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
        tildeltEnhetsnr = navKontor
        opprettetAvEnhetsnr = "9999"
        behandlesAvApplikasjon = "FS22" // Gosys
        orgnr = receivedSykmelding.legekontorOrgNr ?: ""
        beskrivelse = "Manuell behandling sykmelding: ${results.ruleHits}"
        temagruppe = "SYM"
        tema = ""
        behandlingstema = "BEH_EL_SYM"
        oppgavetype = ""
        behandlingstype = ""
        mappeId = 1
        aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
        fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
        prioritet = PrioritetType.NORM
        metadata = mapOf()
    }))

    log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave $logKeys", *logValues)
}

fun findNavOffice(finnBehandlendeEnhetListeResponse: FinnBehandlendeEnhetListeResponse?): String =
        if (finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId == null) {
            "0393"
        } else {
            finnBehandlendeEnhetListeResponse.behandlendeEnhetListe.first().enhetId
        }