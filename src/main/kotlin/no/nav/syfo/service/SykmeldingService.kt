package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDateTime
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.log
import no.nav.syfo.metrics.PAPIRSM_FORDELINGSOPPGAVE
import no.nav.syfo.metrics.PAPIRSM_MAPPET
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_NORGE
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_UTEN_BRUKER
import no.nav.syfo.metrics.PAPIRSM_OPPGAVE
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.toString
import org.apache.kafka.clients.producer.KafkaProducer

@KtorExperimentalAPI
class SykmeldingService constructor(
    private val sakClient: SakClient,
    private val oppgaveService: OppgaveService,
    private val safDokumentClient: SafDokumentClient,
    private val norskHelsenettClient: NorskHelsenettClient,
    private val aktoerIdClient: AktoerIdClient,
    private val regelClient: RegelClient
) {
    suspend fun behandleSykmelding(
        journalpostId: String,
        fnr: String?,
        aktorId: String?,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
        syfoserviceProducer: MessageProducer,
        session: Session,
        sm2013AutomaticHandlingTopic: String,
        kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
        kuhrSarClient: SarClient
    ) {
        log.info("Mottatt norsk papirsykmelding, {}", fields(loggingMeta))
        PAPIRSM_MOTTATT_NORGE.inc()

        if (aktorId.isNullOrEmpty() || fnr.isNullOrEmpty()) {
            PAPIRSM_MOTTATT_UTEN_BRUKER.inc()
            log.info("Papirsykmelding mangler bruker, oppretter fordelingsoppgave: {}", fields(loggingMeta))

            val oppgave = oppgaveService.opprettFordelingsOppgave(journalpostId = journalpostId, gjelderUtland = false, trackingId = sykmeldingId, loggingMeta = loggingMeta)

            if (!oppgave.duplikat) {
                PAPIRSM_FORDELINGSOPPGAVE.inc()
                log.info("Opprettet fordelingsoppgave med {}, {} {}",
                        StructuredArguments.keyValue("oppgaveId", oppgave.oppgaveId),
                        StructuredArguments.keyValue("journalpostId", journalpostId),
                        fields(loggingMeta)
                )
            }
        } else {
            dokumentInfoId?.let {
                try {
                    val ocrFil = safDokumentClient.hentDokument(journalpostId = journalpostId, dokumentInfoId = it, msgId = sykmeldingId, loggingMeta = loggingMeta)

                    ocrFil?.let {
                        val sykmelder = hentSykmelder(ocrFil = ocrFil, sykmeldingId = sykmeldingId, loggingMeta = loggingMeta)

                        val samhandlerInfo = kuhrSarClient.getSamhandler(sykmelder.fnr)
                        val samhandlerPraksisMatch = findBestSamhandlerPraksis(
                                samhandlerInfo,
                                loggingMeta)
                        val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis

                        val fellesformat = mapOcrFilTilFellesformat(
                                skanningmetadata = ocrFil,
                                fnr = fnr,
                                sykmelder = sykmelder,
                                sykmeldingId = sykmeldingId,
                                loggingMeta = loggingMeta)

                        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                        val msgHead = fellesformat.get<XMLMsgHead>()

                        val sykmelding = healthInformation.toSykmelding(
                                sykmeldingId = sykmeldingId,
                                pasientAktoerId = aktorId,
                                legeAktoerId = sykmelder.aktorId,
                                msgId = sykmeldingId,
                                signaturDato = msgHead.msgInfo.genDate
                        )

                        val receivedSykmelding = ReceivedSykmelding(
                                sykmelding = sykmelding,
                                personNrPasient = fnr,
                                tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                                personNrLege = sykmelder.fnr,
                                navLogId = sykmeldingId,
                                msgId = sykmeldingId,
                                legekontorOrgNr = null,
                                legekontorOrgName = "",
                                legekontorHerId = null,
                                legekontorReshId = null,
                                mottattDato = datoOpprettet ?: msgHead.msgInfo.genDate,
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
                                    safDokumentClient,
                                    journalpostId,
                                    loggingMeta
                            )
                            Status.MANUAL_PROCESSING -> handleManuell(
                                    sakClient,
                                    aktorId,
                                    sykmeldingId,
                                    oppgaveService,
                                    journalpostId,
                                    loggingMeta
                            )
                            else -> throw IllegalStateException("Ukjent status: ${validationResult.status} , Papirsykmeldinger kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
                        }
                    }
                } catch (e: Exception) {
                    PAPIRSM_MAPPET.labels("feil").inc()
                    log.warn("Noe gikk galt ved mapping fra OCR til sykmeldingsformat: ${e.message}, {}", fields(loggingMeta))

                    val sakId = sakClient.finnEllerOpprettSak(sykmeldingsId = sykmeldingId, aktorId = aktorId, loggingMeta = loggingMeta)

                    val oppgave = oppgaveService.opprettOppgave(aktoerIdPasient = aktorId, sakId = sakId,
                            journalpostId = journalpostId, gjelderUtland = false, trackingId = sykmeldingId, loggingMeta = loggingMeta)

                    if (!oppgave.duplikat) {
                        log.info("Opprettet oppgave med {}, {} {}",
                                StructuredArguments.keyValue("oppgaveId", oppgave.oppgaveId),
                                StructuredArguments.keyValue("sakid", sakId),
                                fields(loggingMeta)
                        )
                        PAPIRSM_OPPGAVE.inc()
                    } else {
                        log.info("duplikat oppgave med {}, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgave.oppgaveId))
                    }
                }
            }
        }
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

        val aktorId = aktoerIdClient.finnAktorid(behandlerFraHpr.fnr, sykmeldingId)
        if (aktorId.isNullOrEmpty()) {
            log.warn("Kunne ikke hente aktørid for hpr {}, {}", hprNummer, fields(loggingMeta))
            throw IllegalStateException("Kunne ikke hente aktørid for hpr $hprNummer")
        }

        return Sykmelder(
                hprNummer = hprNummer,
                fnr = behandlerFraHpr.fnr,
                aktorId = aktorId,
                fornavn = behandlerFraHpr.fornavn,
                mellomnavn = behandlerFraHpr.mellomnavn,
                etternavn = behandlerFraHpr.etternavn
        )
    }
}
