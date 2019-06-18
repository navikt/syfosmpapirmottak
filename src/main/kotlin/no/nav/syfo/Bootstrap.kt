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
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.SafJournalpostClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.helpers.retry
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.ArbeidsfordelingKriterier
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Geografi
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Tema
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeRequest
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeResponse
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.GeografiskTilknytning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.util.Properties
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
    val credentials =
        objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())

    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(env, credentials).envOverrides()

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
            "${env.applicationName}-consumer",
            valueDeserializer = KafkaAvroDeserializer::class
    )

    val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
    val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient)
    val safJournalpostClient = SafJournalpostClient(env.safV1Url, oidcClient)
    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient)

    val arbeidsfordelingV1 = createPort<ArbeidsfordelingV1>(env.arbeidsfordelingV1EndpointURL) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
    }

    val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
    }

    launchListeners(
            env,
            applicationState,
            consumerProperties,
            safJournalpostClient,
            personV3,
            arbeidsfordelingV1,
            aktoerIdClient,
            credentials,
            oppgaveClient
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })
}

fun CoroutineScope.createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        launch {
            try {
                action()
            } finally {
                applicationState.running = false
            }
        }

@KtorExperimentalAPI
fun CoroutineScope.launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    safJournalpostClient: SafJournalpostClient,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    aktoerIdClient: AktoerIdClient,
    credentials: VaultCredentials,
    oppgaveClient: OppgaveClient
) {
    val journalfoeringHendelseListeners = 0.until(env.applicationThreads).map {
        val kafkaconsumerJournalfoeringHendelse = KafkaConsumer<String, JournalfoeringHendelseRecord>(consumerProperties)

        kafkaconsumerJournalfoeringHendelse.subscribe(
                listOf(
                        env.dokJournalfoeringV1Topic
                )
        )
        createListener(applicationState) {

            blockingApplicationLogic(
                    applicationState, kafkaconsumerJournalfoeringHendelse, safJournalpostClient,
                    personV3, arbeidsfordelingV1, aktoerIdClient, credentials, oppgaveClient
            )
        }
    }.toList()

    applicationState.initialized = true
    runBlocking { journalfoeringHendelseListeners.forEach { it.join() } }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    consumer: KafkaConsumer<String, JournalfoeringHendelseRecord>,
    safJournalpostClient: SafJournalpostClient,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    aktoerIdClient: AktoerIdClient,
    credentials: VaultCredentials,
    oppgaveClient: OppgaveClient
) = coroutineScope {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val journalfoeringHendelseRecord = consumerRecord.value()

            var logValues = arrayOf(
                keyValue("sykmeldingId", "Missing"),
                keyValue("journalpostId", "Missing"),
                keyValue("hendelsesId", "Missing")
            )

            val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }

            try {
                if (journalfoeringHendelseRecord.temaNytt.toString() == "SYM" &&
                    journalfoeringHendelseRecord.mottaksKanal.toString() == "SKAN_NETS"
                ) {
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

                    log.info("Received paper sicklave, $logKeys", *logValues)

                    log.info("Calling saf graphql on journalpostid: ${journalfoeringHendelseRecord.journalpostId}")
                    val journalpost = safJournalpostClient.getJournalpostMetadata(
                        journalfoeringHendelseRecord.journalpostId.toString()
                    )!!

                    val aktoerIdPasient = journalpost.bruker()!!.id()!!

                    val personnummerDeferred = async {
                        aktoerIdClient.getFnr(
                            listOf(aktoerIdPasient), sykmeldingId, credentials.serviceuserUsername
                        )
                    }

                    val personNummer = personnummerDeferred.await()
                    val pasientIdents = personNummer[aktoerIdPasient]

                    if (pasientIdents == null || pasientIdents.feilmelding != null) {
                        log.info(
                            "Patient not found i aktorRegister $logKeys, {}", *logValues,
                            keyValue("errorMessage", pasientIdents?.feilmelding ?: "No response for FNR")
                        )
                        throw RuntimeException("Unable to handle message with id $journalpostId")
                    }

                    log.info("ForsteIdent ${pasientIdents.identer!!.first().ident}")

                    log.info("ForsteIdentgruppe ${pasientIdents.identer!!.first().identgruppe}")

                    log.info("ForsteGjeldende ${pasientIdents.identer!!.first().gjeldende}")

                    val pasientFNR = pasientIdents.identer!!.find { identInfo -> identInfo.gjeldende && identInfo.identgruppe == "FNR" }!!.ident

                    val geografiskTilknytning = fetchGeografiskTilknytning(personV3, pasientFNR)
                    val finnBehandlendeEnhetListeResponse =
                        fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.geografiskTilknytning)

                    createTask(
                        oppgaveClient,
                        logKeys,
                        logValues,
                        journalpost.sak()!!.arkivsaksnummer()!!,
                        journalfoeringHendelseRecord.journalpostId.toString(),
                        findNavOffice(finnBehandlendeEnhetListeResponse),
                        aktoerIdPasient, sykmeldingId
                    )

                    val currentRequestLatency = requestLatency.observeDuration()

                    log.info(
                        "Message($logKeys) processing took {}s",
                        *logValues,
                        keyValue("latency", currentRequestLatency)
                    )
                }
            } catch (e: Exception) {
                log.error("Exception caught while handling message $logKeys", *logValues, e)
            }
        }
        delay(100)
    }
}

inline fun <reified T> XMLEIFellesformat.get(): T = any.find { it is T } as T

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(readynessCheck = ::doReadynessCheck, livenessCheck = { applicationState.running })
    }
}

@KtorExperimentalAPI
suspend fun createTask(
    oppgaveClient: OppgaveClient,
    logKeys: String,
    logValues: Array<StructuredArgument>,
    sakId: String,
    journalpostId: String,
    tildeltEnhetsnr: String,
    aktoerId: String,
    sykmeldingId: String
) {
    log.info(
        "Creating oppgave with {}, $logKeys",
        keyValue("sakid", sakId),
        keyValue("journalpost", journalpostId),
        keyValue("tildeltEnhetsnr", tildeltEnhetsnr),
        *logValues
    )
    val opprettOppgave = OpprettOppgave(
        tildeltEnhetsnr = tildeltEnhetsnr,
        aktoerId = aktoerId,
        opprettetAvEnhetsnr = "9999",
        journalpostId = journalpostId,
        behandlesAvApplikasjon = "FS22",
        saksreferanse = sakId,
        beskrivelse = "Papirsykmelding som må legges inn i infotrygd manuelt",
        tema = "ANY",
        oppgavetype = "JFR",
        aktivDato = LocalDate.now(),
        fristFerdigstillelse = LocalDate.now().plusDays(1),
        prioritet = "NORM"
    )

    val response = oppgaveClient.createOppgave(opprettOppgave, sykmeldingId)
    OPPRETT_OPPGAVE_COUNTER.inc()
    log.info(
        "Task created with {} $logKeys",
        keyValue("oppgaveId", response.id),
        keyValue("sakid", sakId),
        keyValue("journalpost", journalpostId),
        *logValues
    )
}

fun findNavOffice(finnBehandlendeEnhetListeResponse: FinnBehandlendeEnhetListeResponse?): String =
    if (finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId == null) {
        "0393"
    } else {
        finnBehandlendeEnhetListeResponse.behandlendeEnhetListe.first().enhetId
    }

suspend fun fetchGeografiskTilknytning(personV3: PersonV3, patientFnr: String): HentGeografiskTilknytningResponse =
    retry(
        callName = "tps_hent_geografisktilknytning",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
        legalExceptions = *arrayOf(IOException::class, WstxException::class)
    ) {
        personV3.hentGeografiskTilknytning(
            HentGeografiskTilknytningRequest().withAktoer(
                PersonIdent().withIdent(
                    NorskIdent()
                        .withIdent(patientFnr)
                        .withType(Personidenter().withValue("FNR"))
                )
            )
        )
    }

suspend fun fetchBehandlendeEnhet(
    arbeidsfordelingV1: ArbeidsfordelingV1,
    geografiskTilknytning: GeografiskTilknytning?
): FinnBehandlendeEnhetListeResponse? =
    retry(
        callName = "finn_nav_kontor",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
        legalExceptions = *arrayOf(IOException::class, WstxException::class)
    ) {
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
