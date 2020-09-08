package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MAPPET
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_NORGE
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.toString
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.AktoerId
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import org.apache.kafka.clients.producer.KafkaProducer
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.jms.MessageProducer
import javax.jms.Session

@KtorExperimentalAPI
class SykmeldingService(
    private val sakClient: SakClient,
    private val oppgaveService: OppgaveService,
    private val safDokumentClient: SafDokumentClient,
    private val norskHelsenettClient: NorskHelsenettClient,
    private val regelClient: RegelClient,
    private val kuhrSarClient: SarClient,
    private val pdlPersonService: PdlPersonService
) {

    val pilotkontor = listOf("0415", "0412", "0403", "0417", "1101", "1108", "1102", "1129", "1106",
            "1111", "1112", "1119", "1120", "1122", "1124", "1127", "1130", "1133", "1134",
            "1135", "1146", "1149", "1151", "1160", "1161", "1162", "1164", "1165", "1169", "1167", "1168")

    suspend fun behandleSykmelding(
        journalpostId: String,
        pasient: PdlPerson?,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
        syfoserviceProducer: MessageProducer,
        session: Session,
        sm2013AutomaticHandlingTopic: String,
        kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
        dokArkivClient: DokArkivClient,
        kafkaproducerPapirSmRegistering: KafkaProducer<String, PapirSmRegistering>,
        sm2013SmregistreringTopic: String,
        cluster: String,
        personV3: PersonV3
    ) {
        log.info("Mottatt norsk papirsykmelding, {}", fields(loggingMeta))
        PAPIRSM_MOTTATT_NORGE.inc()

        var sykmelder: Sykmelder? = null
        var ocrFil: Skanningmetadata? = null
        var geografiskTilknytning: String? = null
        if (pasient?.aktorId == null || pasient.fnr == null) {
            oppgaveService.opprettFordelingsOppgave(journalpostId = journalpostId, gjelderUtland = false, trackingId = sykmeldingId, loggingMeta = loggingMeta)
            return
        } else {
            val geografiskTIlknytningResposnse = personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(AktoerId().withAktoerId(pasient.aktorId)))
            geografiskTilknytning = geografiskTIlknytningResposnse.geografiskTilknytning.geografiskTilknytning
            dokumentInfoId?.let {
                try {
                    ocrFil = safDokumentClient.hentDokument(journalpostId = journalpostId, dokumentInfoId = it, msgId = sykmeldingId, loggingMeta = loggingMeta)

                    ocrFil?.let { ocr ->

                        sykmelder = hentSykmelder(ocrFil = ocr, sykmeldingId = sykmeldingId, loggingMeta = loggingMeta)

                        val samhandlerInfo = kuhrSarClient.getSamhandler(sykmelder!!.fnr)
                        val samhandlerPraksisMatch = findBestSamhandlerPraksis(
                            samhandlerInfo,
                            loggingMeta)
                        val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis

                        val fellesformat = mapOcrFilTilFellesformat(
                            skanningmetadata = ocr,
                            sykmelder = sykmelder!!,
                            sykmeldingId = sykmeldingId,
                            loggingMeta = loggingMeta, pdlPerson = pasient)

                        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                        val msgHead = fellesformat.get<XMLMsgHead>()

                        val sykmelding = healthInformation.toSykmelding(
                            sykmeldingId = sykmeldingId,
                            pasientAktoerId = pasient.aktorId,
                            legeAktoerId = sykmelder!!.aktorId,
                            msgId = sykmeldingId,
                            signaturDato = msgHead.msgInfo.genDate
                        )

                        val receivedSykmelding = ReceivedSykmelding(
                            sykmelding = sykmelding,
                            personNrPasient = pasient.fnr,
                            tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                            personNrLege = sykmelder!!.fnr,
                            navLogId = sykmeldingId,
                            msgId = sykmeldingId,
                            legekontorOrgNr = null,
                            legekontorOrgName = "",
                            legekontorHerId = null,
                            legekontorReshId = null,
                            mottattDato = (datoOpprettet
                                ?: msgHead.msgInfo.genDate).atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                            rulesetVersion = healthInformation.regelSettVersjon,
                            fellesformat = fellesformatMarshaller.toString(fellesformat),
                            tssid = samhandlerPraksis?.tss_ident ?: ""
                        )

                        log.info("Sykmelding mappet til internt format uten feil {}", fields(loggingMeta))
                        PAPIRSM_MAPPET.labels("ok").inc()

                        log.info("Validerer sykmelding mot regler, {}", fields(loggingMeta))
                        val validationResult = regelClient.valider(receivedSykmelding, sykmeldingId)
                        log.info("Resultat: {}, {}, {}",
                            StructuredArguments.keyValue("ruleStatus", validationResult.status.name),
                            StructuredArguments.keyValue("ruleHits", validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                            fields(loggingMeta)
                        )
                        when (validationResult.status) {
                            Status.OK -> handleOk(
                                kafkaproducerreceivedSykmelding,
                                sm2013AutomaticHandlingTopic,
                                receivedSykmelding,
                                session,
                                syfoserviceProducer,
                                receivedSykmelding.sykmelding.id,
                                healthInformation,
                                dokArkivClient,
                                journalpostId,
                                loggingMeta
                            )
                            Status.MANUAL_PROCESSING -> manuellBehandling(
                                journalpostId = journalpostId,
                                fnr = pasient.fnr,
                                aktorId = pasient.aktorId,
                                dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                                loggingMeta = loggingMeta,
                                sykmeldingId = sykmeldingId,
                                sykmelder = sykmelder,
                                ocrFil = ocrFil,
                                kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                                sm2013SmregistreringTopic = sm2013SmregistreringTopic,
                                cluster = cluster,
                                geografiskTilknytning = geografiskTilknytning
                            )
                            else -> throw IllegalStateException("Ukjent status: ${validationResult.status} , Papirsykmeldinger kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
                        }
                        log.info("Sykmelding håndtert automatisk {}", fields(loggingMeta))
                        return
                    }
                } catch (e: Exception) {
                    PAPIRSM_MAPPET.labels("feil").inc()
                    log.warn("Noe gikk galt ved mapping fra OCR til sykmeldingsformat: ${e.message}, {}", fields(loggingMeta))
                }
            }
            manuellBehandling(
                journalpostId = journalpostId,
                fnr = pasient.fnr,
                aktorId = pasient.aktorId,
                dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                loggingMeta = loggingMeta,
                sykmeldingId = sykmeldingId,
                sykmelder = sykmelder,
                ocrFil = ocrFil,
                kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                sm2013SmregistreringTopic = sm2013SmregistreringTopic,
                cluster = cluster,
                geografiskTilknytning = geografiskTilknytning
            )
        }
    }

    suspend fun manuellBehandling(
        journalpostId: String,
        fnr: String,
        aktorId: String,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
        sykmelder: Sykmelder?,
        ocrFil: Skanningmetadata?,
        kafkaproducerPapirSmRegistering: KafkaProducer<String, PapirSmRegistering>,
        sm2013SmregistreringTopic: String,
        cluster: String,
        geografiskTilknytning: String?
    ) {
        // TODO remove dev-fss and comment in shouldSendToSmregistrering
        if (cluster == "dev-fss" /*&& shouldSendToSmregistrering(geografiskTilknytning) */) {
            log.info("Går til smregistrering fordi dette er dev {}", fields(loggingMeta))
            val papirSmRegistering = mapOcrFilTilPapirSmRegistrering(
                journalpostId = journalpostId,
                fnr = fnr,
                aktorId = aktorId,
                dokumentInfoId = dokumentInfoId,
                datoOpprettet = datoOpprettet?.atZone(ZoneId.systemDefault())?.withZoneSameInstant(ZoneOffset.UTC)?.toOffsetDateTime(),
                sykmeldingId = sykmeldingId,
                sykmelder = sykmelder,
                ocrFil = ocrFil
            )

            val duplikatOppgave = oppgaveService.duplikatOppgave(
                journalpostId = journalpostId, trackingId = sykmeldingId, loggingMeta = loggingMeta)

            if (!duplikatOppgave) {
                sendPapirSmRegistreringToKafka(kafkaproducerPapirSmRegistering, sm2013SmregistreringTopic, papirSmRegistering, loggingMeta)
            } else {
                log.info("duplikat oppgave {}", fields(loggingMeta))
            }
        } else {
            val sakId = sakClient.finnEllerOpprettSak(sykmeldingsId = sykmeldingId, aktorId = aktorId, loggingMeta = loggingMeta)
            oppgaveService.opprettOppgave(aktoerIdPasient = aktorId, sakId = sakId,
                journalpostId = journalpostId, gjelderUtland = false, trackingId = sykmeldingId, loggingMeta = loggingMeta)
        }
    }

    private fun shouldSendToSmregistrering(geografiskTilknytning: String?): Boolean {
        return geografiskTilknytning?.let { pilotkontor.contains(it) } ?: false
    }

    suspend fun hentSykmelder(ocrFil: Skanningmetadata, sykmeldingId: String, loggingMeta: LoggingMeta): Sykmelder {
        if (ocrFil.sykemeldinger.behandler == null || ocrFil.sykemeldinger.behandler.hpr == null) {
            log.warn("Mangler informasjon om behandler, avbryter.. {}", fields(loggingMeta))
            throw IllegalStateException("Mangler informasjon om behandler")
        }
        val hprNummer = ocrFil.sykemeldinger.behandler.hpr.toString()
        if (hprNummer.isEmpty()) {
            log.warn("HPR-nummer mangler, kan ikke fortsette {}", fields(loggingMeta))
            throw IllegalStateException("HPR-nummer mangler")
        }

        val behandlerFraHpr = norskHelsenettClient.finnBehandler(hprNummer, sykmeldingId)

        if (behandlerFraHpr == null || behandlerFraHpr.fnr.isNullOrEmpty()) {
            log.warn("Kunne ikke hente fnr for hpr {}, {}", hprNummer, fields(loggingMeta))
            throw IllegalStateException("Kunne ikke hente fnr for hpr $hprNummer")
        }

        val behandler = pdlPersonService.getPersonnavn(behandlerFraHpr.fnr, loggingMeta)
        if (behandler?.aktorId == null) {
            log.warn("Fant ikke aktorId til behandler for HPR {} {}", hprNummer, fields(loggingMeta))
            throw IllegalStateException("Kunne ikke hente aktorId for hpr $hprNummer")
        }

        return Sykmelder(
            hprNummer = hprNummer,
            fnr = behandlerFraHpr.fnr,
            aktorId = behandler.aktorId,
            fornavn = behandler.navn.fornavn,
            mellomnavn = behandler.navn.mellomnavn,
            etternavn = behandler.navn.etternavn,
            telefonnummer = ocrFil.sykemeldinger.behandler.telefon?.toString()
        )
    }
}
