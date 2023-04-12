package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sykSkanningMeta.Skanningmetadata
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafNotFoundException
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.log
import no.nav.syfo.metrics.FEILARSAK
import no.nav.syfo.metrics.PAPIRSM_MAPPET
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_MED_OCR_UTEN_INNHOLD
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class SykmeldingService(
    private val oppgaveService: OppgaveService,
    private val safDokumentClient: SafDokumentClient,
    private val norskHelsenettClient: NorskHelsenettClient,
    private val regelClient: RegelClient,
    private val kuhrSarClient: SarClient,
    private val pdlPersonService: PdlPersonService,
    private val okSykmeldingTopic: String,
    private val kafkaReceivedSykmeldingProducer: KafkaProducer<String, ReceivedSykmelding>,
    private val dokArkivClient: DokArkivClient,
    private val kafkaproducerPapirSmRegistering: KafkaProducer<String, PapirSmRegistering>,
    private val smregistreringTopic: String,
) {
    suspend fun behandleSykmelding(
        journalpostId: String,
        pasient: PdlPerson?,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        dokumentInfoIdPdf: String,
        temaEndret: Boolean,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ) {
        log.info("Mottatt norsk papirsykmelding, {}", fields(loggingMeta))
        PAPIRSM_MOTTATT_NORGE.inc()

        var sykmelder: Sykmelder? = null
        var ocrFil: Skanningmetadata? = null
        if (pasient?.aktorId == null || pasient.fnr == null) {
            oppgaveService.opprettFordelingsOppgave(journalpostId = journalpostId, gjelderUtland = false, trackingId = sykmeldingId, loggingMeta = loggingMeta)
            return
        } else {
            try {
                ocrFil = if (!dokumentInfoId.isNullOrEmpty()) {
                    safDokumentClient.hentDokument(
                        journalpostId = journalpostId,
                        dokumentInfoId = dokumentInfoId,
                        msgId = sykmeldingId,
                        loggingMeta = loggingMeta,
                    )
                } else {
                    null
                }

                ocrFil?.let { ocr ->

                    sykmelder = hentSykmelder(ocrFil = ocr, sykmeldingId = sykmeldingId, loggingMeta = loggingMeta)

                    val samhandlerInfo = kuhrSarClient.getSamhandler(sykmelder!!.fnr, sykmeldingId)
                    val samhandlerPraksis = findBestSamhandlerPraksis(
                        samhandlerInfo,
                    )

                    log.info("Fant ${when (samhandlerPraksis) { null -> "ikke " else -> "" }}samhandlerpraksis for hpr ${sykmelder!!.hprNummer}")

                    tellOcrInnhold(ocr, loggingMeta)

                    val fellesformat = mapOcrFilTilFellesformat(
                        skanningmetadata = ocr,
                        sykmelder = sykmelder!!,
                        sykmeldingId = sykmeldingId,
                        loggingMeta = loggingMeta,
                        pdlPerson = pasient,
                        journalpostId = journalpostId,
                    )

                    val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                    val msgHead = fellesformat.get<XMLMsgHead>()

                    val sykmelding = healthInformation.toSykmelding(
                        sykmeldingId = sykmeldingId,
                        pasientAktoerId = pasient.aktorId,
                        legeAktoerId = sykmelder!!.aktorId,
                        msgId = sykmeldingId,
                        signaturDato = msgHead.msgInfo.genDate,
                    )

                    val receivedSykmelding = ReceivedSykmelding(
                        sykmelding = sykmelding,
                        personNrPasient = pasient.fnr,
                        tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                        personNrLege = sykmelder!!.fnr,
                        legeHprNr = sykmelder!!.hprNummer,
                        legeHelsepersonellkategori = sykmelder?.godkjenninger?.getHelsepersonellKategori(),
                        navLogId = sykmeldingId,
                        msgId = sykmeldingId,
                        legekontorOrgNr = null,
                        legekontorOrgName = "",
                        legekontorHerId = null,
                        legekontorReshId = null,
                        mottattDato = (
                            datoOpprettet
                                ?: msgHead.msgInfo.genDate
                            ).atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                        rulesetVersion = healthInformation.regelSettVersjon,
                        fellesformat = fellesformatMarshaller.toString(fellesformat),
                        tssid = samhandlerPraksis?.tss_ident ?: "",
                        merknader = null,
                        partnerreferanse = null,
                        vedlegg = emptyList(),
                        utenlandskSykmelding = null,
                    )

                    log.info("Sykmelding mappet til internt format uten feil {}", fields(loggingMeta))
                    PAPIRSM_MAPPET.labels("ok").inc()

                    log.info("Validerer sykmelding mot regler, {}", fields(loggingMeta))
                    val validationResult = regelClient.valider(receivedSykmelding, sykmeldingId)
                    log.info(
                        "Resultat: {}, {}, {}",
                        StructuredArguments.keyValue("ruleStatus", validationResult.status.name),
                        StructuredArguments.keyValue("ruleHits", validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                        fields(loggingMeta),
                    )

                    if (validationResult.status == Status.MANUAL_PROCESSING ||
                        requireManuellBehandling(receivedSykmelding)
                    ) {
                        FEILARSAK.labels("manuell_behandling").inc()
                        manuellBehandling(
                            journalpostId = journalpostId,
                            fnr = pasient.fnr,
                            aktorId = pasient.aktorId,
                            dokumentInfoId = dokumentInfoId, datoOpprettet = datoOpprettet,
                            temaEndret = temaEndret,
                            loggingMeta = loggingMeta,
                            sykmeldingId = sykmeldingId,
                            sykmelder = sykmelder,
                            ocrFil = ocrFil,
                            kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                            smregistreringTopic = smregistreringTopic,
                        )
                    } else if (validationResult.status == Status.OK) {
                        handleOk(
                            kafkaReceivedSykmeldingProducer = kafkaReceivedSykmeldingProducer,
                            okSykmeldingTopic = okSykmeldingTopic,
                            receivedSykmelding = receivedSykmelding,
                            sykmeldingId = receivedSykmelding.sykmelding.id,
                            dokArkivClient = dokArkivClient,
                            journalpostid = journalpostId,
                            loggingMeta = loggingMeta,
                        )
                    } else {
                        throw IllegalStateException("Ukjent status: ${validationResult.status}. Papirsykmeldinger kan kun ha en av to typer statuser: OK eller MANUAL_PROCESSING")
                    }

                    log.info("Sykmelding håndtert automatisk {}", fields(loggingMeta))
                    return
                }
            } catch (e: SafNotFoundException) {
                log.warn("Noe gikk galt ved uthenting av dokument: ${e.message}")
            } catch (e: Exception) {
                tellFeilArsak(e.message)
                PAPIRSM_MAPPET.labels("feil").inc()
                log.warn("Noe gikk galt ved mapping fra OCR til sykmeldingsformat: ${e.message}, {}", fields(loggingMeta))
                try {
                    log.warn(e.stackTraceToString())
                } catch (exception: Exception) {
                    log.info("Failed to log stackTrace")
                }
            }

            // Fallback hvis OCR er null ELLER parsing av OCR til sykmeldingformat mislykkes
            manuellBehandling(
                journalpostId = journalpostId,
                fnr = pasient.fnr,
                aktorId = pasient.aktorId,
                dokumentInfoId = dokumentInfoId ?: dokumentInfoIdPdf,
                temaEndret = temaEndret,
                datoOpprettet = datoOpprettet,
                loggingMeta = loggingMeta,
                sykmeldingId = sykmeldingId,
                sykmelder = sykmelder,
                ocrFil = ocrFil,
                kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                smregistreringTopic = smregistreringTopic,
            )
        }
    }

    suspend fun manuellBehandling(
        journalpostId: String,
        fnr: String,
        aktorId: String,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        temaEndret: Boolean,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
        sykmelder: Sykmelder?,
        ocrFil: Skanningmetadata?,
        kafkaproducerPapirSmRegistering: KafkaProducer<String, PapirSmRegistering>,
        smregistreringTopic: String,
    ) {
        log.info("Ruter oppgaven til smregistrering {}", fields(loggingMeta))
        val oppgave = oppgaveService.hentOppgave(journalpostId, sykmeldingId)

        if (oppgave.antallTreffTotalt == 0 || oppgave.antallTreffTotalt > 0 && temaEndret) {
            val papirSmRegistering = mapOcrFilTilPapirSmRegistrering(
                journalpostId = journalpostId,
                oppgaveId = oppgave.oppgaver.firstOrNull()?.id?.toString(),
                fnr = fnr,
                aktorId = aktorId,
                dokumentInfoId = dokumentInfoId,
                datoOpprettet = datoOpprettet?.atZone(ZoneId.systemDefault())?.withZoneSameInstant(ZoneOffset.UTC)?.toOffsetDateTime(),
                sykmeldingId = sykmeldingId,
                sykmelder = sykmelder,
                ocrFil = ocrFil,
            )
            sendPapirSmRegistreringToKafka(kafkaproducerPapirSmRegistering, smregistreringTopic, papirSmRegistering, loggingMeta)
        } else {
            log.info("duplikat oppgave {}", fields(loggingMeta))
        }
    }

    private fun requireManuellBehandling(receivedSykmelding: ReceivedSykmelding): Boolean {
        val minFom = receivedSykmelding.sykmelding.perioder.minByOrNull { periode -> periode.fom }?.fom
        val maxTom = receivedSykmelding.sykmelding.perioder.maxByOrNull { periode: Periode -> periode.tom }?.tom
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
            telefonnummer = ocrFil.sykemeldinger.behandler.telefon?.toString(),
            godkjenninger = behandlerFraHpr.godkjenninger,
        )
    }

    private fun tellOcrInnhold(ocr: Skanningmetadata, loggingMeta: LoggingMeta) {
        if (ocr.sykemeldinger.medisinskVurdering.hovedDiagnose.isEmpty() &&
            ocr.sykemeldinger.medisinskVurdering.bidiagnose.isEmpty() &&
            ocr.sykemeldinger.medisinskVurdering.annenFraversArsak.isNullOrEmpty()
        ) {
            log.info("Papirsykmelding inneholder ikke hovedDiagnose, biDiagnose eller annenFraversArsak", fields(loggingMeta))
            PAPIRSM_MOTTATT_MED_OCR_UTEN_INNHOLD.inc()
        }
    }

    private fun tellFeilArsak(feilmelding: String?) {
        val label = feilmelding?.let {
            if (it.startsWith("Kunne ikke hente fnr for hpr")) {
                "fant_ikke_behandler_hpr"
            } else if (it.startsWith("Mangler informasjon om behandler")) {
                "ocr_mangler_behandler"
            } else if (it.startsWith("HPR-nummer mangler")) {
                "ocr_mangler_hpr"
            } else if (it.startsWith("skanningmetadata.sykemeldinger.pasient must not be null")) {
                "ocr_mangler_pasient"
            } else if (it.startsWith("ocr.sykemeldinger.medisinskVurdering must not be null")) {
                "ocr_mangler_medisinskvurdering"
            } else if (it.contains("diagnosekode must not be null")) {
                "diagnosekode_mangler"
            } else if (it.contains("tilhører ingen kjente kodeverk")) {
                "ukjent_diagnosekode"
            } else if (it.startsWith("periodeTOMDato must not be null")) {
                "mangler_tom"
            } else if (it.startsWith("periodeFOMDato must not be null")) {
                "mangler_fom"
            } else {
                "annet"
            }
        } ?: "null"
        FEILARSAK.labels(label).inc()
    }
}

fun List<Godkjenning>.getHelsepersonellKategori(): String? = when {
    find { it.helsepersonellkategori?.verdi == "LE" } != null -> "LE"
    find { it.helsepersonellkategori?.verdi == "TL" } != null -> "TL"
    find { it.helsepersonellkategori?.verdi == "MT" } != null -> "MT"
    find { it.helsepersonellkategori?.verdi == "FT" } != null -> "FT"
    find { it.helsepersonellkategori?.verdi == "KI" } != null -> "KI"
    else -> {
        val verdi = firstOrNull()?.helsepersonellkategori?.verdi
        log.warn("Signerende behandler har ikke en helsepersonellkategori($verdi) vi kjenner igjen")
        verdi
    }
}
