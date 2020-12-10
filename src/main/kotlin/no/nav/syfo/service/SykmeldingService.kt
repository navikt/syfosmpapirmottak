package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafNotFoundException
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_MAPPET
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_NORGE
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.toString
import org.apache.kafka.clients.producer.KafkaProducer

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
        sm2013SmregistreringTopic: String
    ) {
        log.info("Mottatt norsk papirsykmelding, {}", fields(loggingMeta))
        PAPIRSM_MOTTATT_NORGE.inc()

        var sykmelder: Sykmelder? = null
        var ocrFil: Skanningmetadata? = null
        if (pasient?.aktorId == null || pasient.fnr == null) {
            oppgaveService.opprettFordelingsOppgave(journalpostId = journalpostId, gjelderUtland = false, trackingId = sykmeldingId, loggingMeta = loggingMeta)
            return
        } else {
            if (dokumentInfoId.isNullOrEmpty()) {
                // Opprett vanlig journalføringsoppgave
                opprettJournalfoeringsoppgave(journalpostId = journalpostId, sykmeldingsId = sykmeldingId, aktorId = pasient.aktorId, loggingMeta = loggingMeta)
            } else {

                try {
                    ocrFil = safDokumentClient.hentDokument(journalpostId = journalpostId, dokumentInfoId = dokumentInfoId, msgId = sykmeldingId, loggingMeta = loggingMeta)

                    ocrFil?.let { ocr ->

                        sykmelder = hentSykmelder(ocrFil = ocr, sykmeldingId = sykmeldingId, loggingMeta = loggingMeta)

                        val samhandlerInfo = kuhrSarClient.getSamhandler(sykmelder!!.fnr)
                        val samhandlerPraksis = findBestSamhandlerPraksis(
                                samhandlerInfo)

                        log.info("Fant ${when (samhandlerPraksis) { null -> "ikke " else -> "" }}samhandlerpraksis for hpr ${sykmelder!!.hprNummer}")

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

                        if (validationResult.status == Status.MANUAL_PROCESSING ||
                                requireManuellBehandling(receivedSykmelding)) {
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
                                    sm2013SmregistreringTopic = sm2013SmregistreringTopic
                            )
                        } else if (validationResult.status == Status.OK) {
                            handleOk(
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
                        } else {
                            throw IllegalStateException("Ukjent status: ${validationResult.status}. Papirsykmeldinger kan kun ha en av to typer statuser: OK eller MANUAL_PROCESSING")
                        }

                        log.info("Sykmelding håndtert automatisk {}", fields(loggingMeta))
                        return
                    }
                } catch (e: SafNotFoundException) {
                    log.warn("Noe gikk galt ved uthenting av dokument: ${e.message}")
                    return opprettJournalfoeringsoppgave(journalpostId = journalpostId, sykmeldingsId = sykmeldingId, aktorId = pasient.aktorId, loggingMeta = loggingMeta)
                } catch (e: Exception) {
                    PAPIRSM_MAPPET.labels("feil").inc()
                    log.warn("Noe gikk galt ved mapping fra OCR til sykmeldingsformat: ${e.message}, {}", fields(loggingMeta))
                }

                // Fallback hvis OCR er null ELLER parsing av OCR til sykmeldingformat mislykkes
                manuellBehandling(
                        journalpostId = journalpostId,
                        fnr = pasient.fnr,
                        aktorId = pasient.aktorId,
                        dokumentInfoId = dokumentInfoId,
                        datoOpprettet = datoOpprettet,
                        loggingMeta = loggingMeta,
                        sykmeldingId = sykmeldingId,
                        sykmelder = sykmelder,
                        ocrFil = ocrFil,
                        kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                        sm2013SmregistreringTopic = sm2013SmregistreringTopic
                )
            }
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
        sm2013SmregistreringTopic: String
    ) {
        log.info("Ruter oppgaven til smregistrering", fields(loggingMeta))
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
    }

    private suspend fun opprettJournalfoeringsoppgave(journalpostId: String, sykmeldingsId: String, aktorId: String, loggingMeta: LoggingMeta) {
        val sakId = sakClient.finnEllerOpprettSak(sykmeldingsId = sykmeldingsId, aktorId = aktorId, loggingMeta = loggingMeta)
        oppgaveService.opprettOppgave(aktoerIdPasient = aktorId, sakId = sakId,
                journalpostId = journalpostId, gjelderUtland = false, trackingId = sykmeldingsId, loggingMeta = loggingMeta)
    }

    private fun requireManuellBehandling(receivedSykmelding: ReceivedSykmelding): Boolean {
        val minFom = receivedSykmelding.sykmelding.perioder.minBy { periode -> periode.fom }?.fom
        val maxTom = receivedSykmelding.sykmelding.perioder.maxBy { periode: Periode -> periode.tom }?.tom
        val today = LocalDate.now()

        val limit = 90

        if (ChronoUnit.DAYS.between(minFom, maxTom) > limit) {
            log.info("Sender oppgave til manuell kontroll fordi avstanden mellom fom og tom er større enn $limit")
            return true
        } else if (ChronoUnit.DAYS.between(minFom, today) > limit) {
            log.info("Sender oppgave til manuell kontroll fordi avstanden mellom fom og dagens dato er større enn $limit")
            return true
        }

        return false
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

        val behandler = pdlPersonService.getPdlPerson(behandlerFraHpr.fnr, loggingMeta)
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
