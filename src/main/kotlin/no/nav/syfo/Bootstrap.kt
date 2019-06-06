package no.nav.syfo

import com.ctc.wstx.exc.WstxException
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.sykSkanningMeta.SykemeldingerType
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.client.JournalfoerInngaaendeV1Client
import no.nav.syfo.client.SafClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.helpers.retry
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.util.sykemeldingerTypeUnmarshaller
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
import no.nhn.schemas.reg.hprv2.IHPR2Service
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.StringReader
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.datatype.DatatypeFactory
import no.nhn.schemas.reg.hprv2.Person as HPRPerson
import org.apache.cxf.phase.Phase
import org.apache.cxf.binding.soap.SoapMessage
import org.apache.cxf.message.Message
import org.apache.cxf.ws.addressing.WSAddressingFeature
import java.time.LocalDateTime

fun doReadynessCheck(): Boolean {
    return true
}

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)
val coroutineContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

val datatypeFactory: DatatypeFactory = DatatypeFactory.newInstance()

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
                val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)

                val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient)

                val journalfoerInngaaendeV1Client = JournalfoerInngaaendeV1Client(env.journalfoerInngaaendeV1URL, oidcClient)

                val safClient = SafClient(env.safURL, oidcClient)

                val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient)

                val kuhrSarClient = SarClient(env.kuhrSarApiUrl, credentials)

                val kafkaBaseConfig = loadBaseConfig(env, credentials)

                val consumerProperties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = KafkaAvroDeserializer::class)

                val kafkaconsumer = KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.dokJournalfoeringV1Topic))

                val arbeidsfordelingV1 = createPort<ArbeidsfordelingV1>(env.arbeidsfordelingV1EndpointURL) {
                    port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
                }

                val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
                    port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
                }

                val helsepersonellV1 = createPort<IHPR2Service>(env.helsepersonellv1EndpointURL) {
                    proxy {
                        // TODO: Contact someone about this hacky workaround
                        // talk to HDIR about HPR about they claim to send a ISO-8859-1 but its really UTF-8 payload
                        val interceptor = object : AbstractSoapInterceptor(Phase.RECEIVE) {
                            override fun handleMessage(message: SoapMessage?) {
                                if (message != null)
                                    message[Message.ENCODING] = "utf-8"
                            }
                        }

                        inInterceptors.add(interceptor)
                        inFaultInterceptors.add(interceptor)
                        features.add(WSAddressingFeature())
                    }

                    port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
                }

                blockingApplicationLogic(applicationState, kafkaconsumer,
                        journalfoerInngaaendeV1Client, safClient, personV3, arbeidsfordelingV1,
                        aktoerIdClient, credentials, kuhrSarClient, helsepersonellV1, env, oppgaveClient)
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
    consumer: KafkaConsumer<String, JournalfoeringHendelseRecord>,
    journalfoerInngaaendeV1Client: JournalfoerInngaaendeV1Client,
    safClient: SafClient,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    aktoerIdClient: AktoerIdClient,
    credentials: VaultCredentials,
    kuhrSarClient: SarClient,
    helsepersonellv1: IHPR2Service,
    env: Environment,
    oppgaveClient: OppgaveClient
) = coroutineScope {
    while (applicationState.running) {
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
                if (journalfoeringHendelseRecord.temaGammelt.toString() == "SYM" &&
                        journalfoeringHendelseRecord.mottaksKanal == "SKAN_NETS") {
                    INCOMING_MESSAGE_COUNTER.inc()
                    val requestLatency = REQUEST_TIME.startTimer()

                    val sykmeldingId = UUID.randomUUID().toString()
                    val journalpostId = journalfoeringHendelseRecord.journalpostId
                    val hendelsesId = journalfoeringHendelseRecord.hendelsesId

                    logValues = arrayOf(
                            keyValue("sykmeldingId", sykmeldingId),
                            keyValue("journalpostId", journalpostId),
                            keyValue("hendelsesId", hendelsesId)
                    )

                    log.info("Received message, $logKeys", *logValues)

                    // TODO remove log.info when in prod
                    log.info("journalfoeringHendelseRecord:", objectMapper.writeValueAsString(journalfoeringHendelseRecord))
                    val journalpost = journalfoerInngaaendeV1Client.getJournalpostMetadata(
                            journalfoeringHendelseRecord.journalpostId)

                    val smpapirOCRDokumentInfoId = journalpost.dokumentListe.first {
                        it.variant.first().variantFormat == "ORIGINAL"
                    }.dokumentId

                    val smpapirPDFDokumentInfoId = journalpost.dokumentListe.first {
                        it.variant.first().variantFormat == "ARKIV"
                    }.dokumentId

                    val smpapirMetadataDokumentInfoId = journalpost.dokumentListe.first {
                        it.variant.first().variantFormat == "SKANNING_META"
                    }.dokumentId

                    log.info("Calling saf rest")
                    // TODO, get the 3 attachments on that spesific journalpost , xml/ocr, pdf,
                    // metadata change return type of safClient based on VariantFormat
                    val smpapirMetadata = safClient.getdokument(
                            journalfoeringHendelseRecord.journalpostId.toString(),
                            smpapirMetadataDokumentInfoId,
                            "SKANNING_META",
                            logKeys,
                            logValues)

                    val smpapirOCR = safClient.getdokument(
                            journalfoeringHendelseRecord.journalpostId.toString(),
                            smpapirOCRDokumentInfoId,
                            "ORIGINAL",
                            logKeys,
                            logValues)

                    val smpapirPDF = safClient.getdokument(
                            journalfoeringHendelseRecord.journalpostId.toString(),
                            smpapirPDFDokumentInfoId,
                            "ARKIV",
                            logKeys,
                            logValues)

                    val sykmeldingpapir = sykemeldingerTypeUnmarshaller.unmarshal(StringReader(objectMapper.writeValueAsString(smpapirOCR))) as SykemeldingerType

                    val hprNrLege = sykmeldingpapir.behandler.hpr.toInt()

                    // TODO make call to HPR, and find out the fnr for the doctor
                    val doctor = fetchDoctor(helsepersonellv1, hprNrLege).await()

                    val personNumberPatient = sykmeldingpapir.pasient.fnr
                    val personNumberDoctor = doctor.nin

                    val aktoerIdsDeferred = async {
                        aktoerIdClient.getAktoerIds(
                                listOf(personNumberDoctor,
                                        personNumberPatient),
                                sykmeldingId,
                                credentials.serviceuserUsername)
                    }

                    // TODO find the orgName?
                    val legekontorOrgName = "Sorlandet sykehus"

                    val samhandlerInfo = kuhrSarClient.getSamhandler(personNumberDoctor)
                    val samhandlerPraksis = findBestSamhandlerPraksis(
                            samhandlerInfo,
                            legekontorOrgName)?.samhandlerPraksis

                    when (samhandlerPraksis) {
                        null -> log.info("SamhandlerPraksis is Not found, $logKeys", *logValues)
                        else -> log.info("SamhandlerPraksis is Not found, $logKeys", *logValues)
                    }

                    val aktoerIds = aktoerIdsDeferred.await()
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

                    val sykmelding = sykmeldingpapir.toSykmelding(
                            sykmeldingId = sykmeldingId,
                            pasientAktoerId = patientIdents!!.identer!!.first().ident,
                            legeAktoerId = doctorIdents!!.identer!!.first().ident,
                            msgId = sykmeldingId
                    )
                    val receivedSykmelding = ReceivedSykmelding(
                            sykmelding = sykmelding,
                            personNrPasient = personNumberPatient,
                            tlfPasient = "",
                            personNrLege = personNumberDoctor,
                            navLogId = sykmeldingId,
                            msgId = sykmeldingId,
                            legekontorOrgNr = "",
                            legekontorOrgName = "",
                            legekontorHerId = "",
                            legekontorReshId = "",
                            mottattDato = LocalDateTime.now(),
                            rulesetVersion = "",
                            fellesformat = "",
                            tssid = samhandlerPraksis?.tss_ident ?: ""
                    )

                    val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)
                    val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.geografiskTilknytning)
                    // TODO find sakid
                    createTask(oppgaveClient,
                            logKeys,
                            logValues,
                            "",
                            journalfoeringHendelseRecord.journalpostId.toString(),
                            findNavOffice(finnBehandlendeEnhetListeResponse),
                            patientIdents!!.identer!!.first().ident, sykmeldingId)

                    val currentRequestLatency = requestLatency.observeDuration()

                    log.info("Message($logKeys) processing took {}s",
                            *logValues,
                            keyValue("latency", currentRequestLatency))
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

suspend fun createTask(oppgaveClient: OppgaveClient, logKeys: String, logValues: Array<StructuredArgument>, sakId: String, journalpostId: String, tildeltEnhetsnr: String, aktoerId: String, sykmeldingId: String) {
    log.info("Creating oppgave with {}, $logKeys",
            keyValue("sakid", sakId),
            keyValue("journalpost", journalpostId),
            keyValue("tildeltEnhetsnr", tildeltEnhetsnr),
            *logValues)
    val opprettOppgave = OpprettOppgave(
            tildeltEnhetsnr = tildeltEnhetsnr,
            aktoerId = aktoerId,
            opprettetAvEnhetsnr = "9999",
            journalpostId = journalpostId,
            behandlesAvApplikasjon = "FS22",
            saksreferanse = sakId,
            beskrivelse = "Papir sykemdligns som må manuelt legges inn i infotrygd",
            tema = "ANY",
            oppgavetype = "BEH_EL_SYM",
            aktivDato = LocalDate.parse(DateTimeFormatter.ISO_DATE.format(LocalDate.now()), DateTimeFormatter.ISO_DATE),
            fristFerdigstillelse = LocalDate.parse(DateTimeFormatter.ISO_DATE.format(LocalDate.now().plusDays(14)), DateTimeFormatter.ISO_DATE),
            prioritet = "NORM"
    )

    val response = oppgaveClient.createOppgave(opprettOppgave, sykmeldingId)
    OPPRETT_OPPGAVE_COUNTER.inc()
    log.info("Task created with {} $logKeys",
            keyValue("oppgaveId", response.id),
            keyValue("sakid", sakId),
            keyValue("journalpost", journalpostId),
            *logValues)
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

fun CoroutineScope.fetchDoctor(hprService: IHPR2Service, hprNummer: Int): Deferred<HPRPerson> = async {
    retry(
            callName = "hpr_hent_person_med_personnummer",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
            legalExceptions = *arrayOf(IOException::class, WstxException::class)
    ) {
        hprService.hentPerson(hprNummer, datatypeFactory.newXMLGregorianCalendar(GregorianCalendar()))
    }
}