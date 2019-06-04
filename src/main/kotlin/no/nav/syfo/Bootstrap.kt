package no.nav.syfo

import com.ctc.wstx.exc.WstxException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.client.JournalfoerInngaaendeV1Client
import no.nav.syfo.client.SafClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.helpers.retry
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeResponse
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.ArbeidsfordelingKriterier
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Geografi
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Tema
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeRequest
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.GeografiskTilknytning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun doReadynessCheck(): Boolean {
    return true
}

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)
val coroutineContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

val log = LoggerFactory.getLogger("nav.syfo.papirmottak")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

@KtorExperimentalAPI
fun main() = runBlocking(coroutineContext) {
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())

    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

    try {
        val listeners = (1..env.applicationThreads).map {
            launch {
                val syfoSykemelginReglerClient = SyfoSykemeldingRuleClient(env.syfoSmRegelerApiURL, credentials)
                val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
                val journalfoerInngaaendeV1Client = JournalfoerInngaaendeV1Client(env.journalfoerInngaaendeV1URL, oidcClient)
                val safClient = SafClient(env.safURL, oidcClient)
                val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient)

                val arbeidsfordelingV1 = createPort<ArbeidsfordelingV1>(env.arbeidsfordelingV1EndpointURL) {
                    port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
                }

                val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
                    port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
                }

                val kafkaBaseConfig = loadBaseConfig(env, credentials)

                val producerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = StringSerializer::class)
                val consumerProperties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = KafkaAvroDeserializer::class)

                val kafkaconsumer = KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.dokJournalfoeringV1Topic))

                val kafkaproducer = KafkaProducer<String, String>(producerProperties)

                val kafkaManualTaskproducerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = KafkaAvroSerializer::class)
                val kafkaManuelTaskProducer = KafkaProducer<String, ProduceTask>(kafkaManualTaskproducerProperties)

                blockingApplicationLogic(applicationState, kafkaproducer, kafkaconsumer, syfoSykemelginReglerClient,
                        journalfoerInngaaendeV1Client, safClient, aktoerIdClient, personV3, arbeidsfordelingV1, kafkaManuelTaskProducer)
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
    syfoSykemelginReglerClient: SyfoSykemeldingRuleClient,
    journalfoerInngaaendeV1Client: JournalfoerInngaaendeV1Client,
    safClient: SafClient,
    aktoerIdClient: AktoerIdClient,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    kafkaManuelTaskProducer: KafkaProducer<String, ProduceTask>
) = coroutineScope {
    loop@ while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            val journalfoeringHendelseRecord = it.value()

            var logValues = arrayOf(
                    keyValue("sykmeldingId", "Missing"),
                    keyValue("journalpostId", "Missing"),
                    keyValue("hendelsesId", "Missing")
            )

            val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }

            try {
                // TODO find a better metod of filter from the kafa topic, only get the right "behandlingstema" and
                // TODO "mottaksKanal"
                if (journalfoeringHendelseRecord.temaNytt.toString() == "SYM" &&
                        journalfoeringHendelseRecord.mottaksKanal == "skanning") {
                    INCOMING_MESSAGE_COUNTER.inc()

                    logValues = arrayOf(
                            keyValue("sykmeldingId", UUID.randomUUID().toString()),
                            keyValue("journalpostId", journalfoeringHendelseRecord.journalpostId),
                            keyValue("hendelsesId", journalfoeringHendelseRecord.hendelsesId)
                    )

                    log.info("Received message, $logKeys", *logValues)

                    // TODO remove when in prod
                    log.info("journalfoeringHendelseRecord:", objectMapper.writeValueAsString(journalfoeringHendelseRecord))
                    val journalpost = journalfoerInngaaendeV1Client.getJournalpostMetadata(
                            journalfoeringHendelseRecord.journalpostId)

                    // TODO is this correct
                    val smpapirXMLDokumentInfoId = journalpost.dokumentListe.first {
                        it.variant.first().variantFormat == "XML"
                    }.dokumentId

                    // TODO is this correct
                    val smpapirPDFDokumentInfoId = journalpost.dokumentListe.first {
                        it.variant.first().variantFormat == "PDF"
                    }.dokumentId

                    // TODO is this correct
                    val smpapirMetadataDokumentInfoId = journalpost.dokumentListe.first {
                        it.variant.first().variantFormat == "ARKIV"
                    }.dokumentId

                    log.info("Calling saf rest")
                    // TODO, get the 3 attachments on that spesific journalpost , xml/ocr, pdf,
                    // metadata change return type of safClient based on VariantFormat
                    val smpapirMetadata = safClient.getdokument(
                            journalfoeringHendelseRecord.journalpostId.toString(),
                            smpapirMetadataDokumentInfoId,
                            "ARKIV",
                            logKeys,
                            logValues)

                    val smpapirXML = safClient.getdokument(
                            journalfoeringHendelseRecord.journalpostId.toString(),
                            smpapirXMLDokumentInfoId,
                            "ARKIV",
                            logKeys,
                            logValues)

                    val smpapirPDF = safClient.getdokument(
                            journalfoeringHendelseRecord.journalpostId.toString(),
                            smpapirPDFDokumentInfoId,
                            "ARKIV",
                            logKeys,
                            logValues)

                    // TODO remove
                    log.info("i made it here")
                    /*
                    val sykmeldingtype = sykemeldingerTypeUnmarshaller.unmarshal(StringReader(objectMapper.writeValueAsString(smpapirMetadata))) as SykemeldingerType

                    val sykmelding = healthInformation.toSykmelding(
                            sykmeldingId = UUID.randomUUID().toString(),
                            pasientAktoerId = patientIdents.identer!!.first().ident,
                            legeAktoerId = doctorIdents.identer!!.first().ident,
                            msgId = msgId,
                            signaturDato = msgHead.msgInfo.genDate
                    )
                    val receivedSykmelding = ReceivedSykmelding(
                            sykmelding = sykmelding,
                            personNrPasient = personNumberPatient,
                            tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                            personNrLege = personNumberDoctor,
                            navLogId = ediLoggId,
                            msgId = msgId,
                            legekontorOrgNr = legekontorOrgNr,
                            legekontorOrgName = legekontorOrgName,
                            legekontorHerId = legekontorHerId,
                            legekontorReshId = legekontorReshId,
                            mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime().toLocalDateTime(),
                            rulesetVersion = healthInformation.regelSettVersjon,
                            fellesformat = inputMessageText,
                            tssid = samhandlerPraksis?.tss_ident ?: ""
                    )


                    val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)
                    val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.geografiskTilknytning)
                    createTask(kafkaManuelTaskProducer, receivedSykmelding, findNavOffice(finnBehandlendeEnhetListeResponse), logKeys, logValues)
                     */
                }
            } catch (e: Exception) {
                log.error("Exception caught while handling message $logKeys", *logValues, e)
            }

            delay(100)
        }
    }
}

inline fun <reified T> XMLEIFellesformat.get(): T = any.find { it is T } as T

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(readynessCheck = ::doReadynessCheck, livenessCheck = { applicationState.running })
    }
}

fun createTask(kafkaProducer: KafkaProducer<String, ProduceTask>, receivedSykmelding: ReceivedSykmelding, navKontor: String, logKeys: String, logValues: Array<StructuredArgument>) {
    kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", receivedSykmelding.sykmelding.id, ProduceTask().apply {
        messageId = receivedSykmelding.msgId
        aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
        tildeltEnhetsnr = navKontor
        opprettetAvEnhetsnr = "9999"
        behandlesAvApplikasjon = "FS22" // Gosys
        orgnr = receivedSykmelding.legekontorOrgNr ?: ""
        beskrivelse = "Manuell behandling av pga papir sykmelding"
        temagruppe = "ANY"
        tema = "SYM"
        behandlingstema = "ANY"
        oppgavetype = "BEH_EL_SYM"
        behandlingstype = "ANY"
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

suspend fun fetchGeografiskTilknytning(personV3: PersonV3, receivedSykmelding: ReceivedSykmelding): HentGeografiskTilknytningResponse =
        retry(callName = "tps_hent_geografisktilknytning",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(PersonIdent().withIdent(
                    NorskIdent()
                            .withIdent(receivedSykmelding.personNrPasient)
                            .withType(Personidenter().withValue("FNR")))))
        }

suspend fun fetchBehandlendeEnhet(arbeidsfordelingV1: ArbeidsfordelingV1, geografiskTilknytning: GeografiskTilknytning?): FinnBehandlendeEnhetListeResponse? =
        retry(callName = "finn_nav_kontor",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            arbeidsfordelingV1.finnBehandlendeEnhetListe(FinnBehandlendeEnhetListeRequest().apply {
                val afk = ArbeidsfordelingKriterier()
                if (geografiskTilknytning?.geografiskTilknytning != null) {
                    afk.geografiskTilknytning = Geografi().apply {
                        value = geografiskTilknytning.geografiskTilknytning
                    }
                }
                afk.tema = Tema().apply {
                    value = "SYM"
                }
                arbeidsfordelingKriterier = afk
            })
        }