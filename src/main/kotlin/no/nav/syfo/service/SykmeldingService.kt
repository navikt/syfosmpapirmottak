package no.nav.syfo.service

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.DokumentVariantFormat
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.Icpc2BDiagnoser
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.NyRegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SafNotFoundException
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.TssException
import no.nav.syfo.domain.DokumentInfo
import no.nav.syfo.domain.PapirSmRegistering
import no.nav.syfo.domain.Sykmelder
import no.nav.syfo.log
import no.nav.syfo.metrics.FEILARSAK
import no.nav.syfo.metrics.OCR_SAMMENLIGNING
import no.nav.syfo.metrics.OCR_SAMMENLIGNING_AVVIK_FELT
import no.nav.syfo.metrics.PAPIRSM_MAPPET
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_MED_OCR_UTEN_INNHOLD
import no.nav.syfo.metrics.PAPIRSM_MOTTATT_NORGE
import no.nav.syfo.metrics.SYKMELDING_OCR_RESULT
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.model.Status
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.securelog
import no.nav.syfo.service.SykmeldingParseResult.Source
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getLocalDateTime
import no.nav.syfo.util.safeUnmarshalSkanningmetadata
import no.nav.syfo.util.toString
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory

sealed interface SykmeldingParseResult {
    enum class Source {
        SCANNING,
        TSM,
    }

    enum class Status {
        NO_RESULT,
        FAILED,
        OK,
    }

    val source: Source
    val status: Status

    class NoResult(override val source: Source) : SykmeldingParseResult {
        override val status: Status = Status.NO_RESULT
    }

    class OK(
        val skanningmetadata: Skanningmetadata,
        val receivedSykmelding: ReceivedSykmelding,
        val sykmelder: Sykmelder,
        override val source: Source,
    ) : SykmeldingParseResult {
        override val status: Status = Status.OK
    }

    class Failed(
        val skanningmetadata: Skanningmetadata,
        val sykmelder: Sykmelder?,
        val errorMessage: String,
        val exception: Exception,
        override val source: Source,
    ) : SykmeldingParseResult {
        override val status: Status = Status.FAILED
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SykmeldingService(
    private val oppgaveService: OppgaveService,
    private val safDokumentClient: SafDokumentClient,
    private val norskHelsenettClient: NorskHelsenettClient,
    private val nyRegelClient: NyRegelClient,
    private val smtssClient: SmtssClient,
    private val pdlPersonService: PdlPersonService,
    private val okSykmeldingTopic: String,
    private val kafkaReceivedSykmeldingProducer:
        KafkaProducer<String, ReceivedSykmeldingWithValidation>,
    private val dokArkivClient: DokArkivClient,
    private val kafkaproducerPapirSmRegistering: KafkaProducer<String, PapirSmRegistering>,
    private val smregistreringTopic: String,
    icpc2BDiagnoserDeffered: Deferred<Map<String, List<Icpc2BDiagnoser>>>,
    private val bucketUploadService: BucketUploadService,
    private val ocrService: OcrService,
) {

    private val log = LoggerFactory.getLogger(SykmeldingService::class.java)

    private var icpc2BDiagnoser: Map<String, List<Icpc2BDiagnoser>> = emptyMap()

    init {
        icpc2BDiagnoserDeffered.invokeOnCompletion { cause ->
            if (cause != null) {
                log.error("Failed to load icpc2Bdiagnoser from api", cause)
            } else {
                icpc2BDiagnoser = icpc2BDiagnoserDeffered.getCompleted()
            }
        }
    }

    suspend fun behandleSykmelding(
        journalpostId: String,
        pasient: PdlPerson?,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        dokumentInfoIdPdf: String,
        temaEndret: Boolean,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
        alleDokumenter: List<DokumentInfo>,
    ) {
        log.info("Mottatt norsk papirsykmelding, {}", fields(loggingMeta))
        PAPIRSM_MOTTATT_NORGE.inc()

        var sykmelder: Sykmelder? = null
        var skanningmetadata: Skanningmetadata? = null
        if (pasient?.aktorId == null || pasient.fnr == null) {
            oppgaveService.opprettFordelingsOppgave(
                journalpostId = journalpostId,
                gjelderUtland = false,
                trackingId = sykmeldingId,
                loggingMeta = loggingMeta,
            )
            return
        } else {
            try {
                val dokumenter =
                    alleDokumenter
                        .filter {
                            it.variantFormat == DokumentVariantFormat.ARKIV ||
                                it.variantFormat == DokumentVariantFormat.ORIGINAL
                        }
                        .map {
                            it to
                                safDokumentClient.getDocument(
                                    journalpostId,
                                    it.dokumentInfoId,
                                    it,
                                    loggingMeta,
                                    sykmeldingId,
                                )
                        }

                val scanningOcrFile =
                    dokumenter
                        .singleOrNull { it.first.variantFormat == DokumentVariantFormat.ORIGINAL }
                        ?.let { safeUnmarshalSkanningmetadata(it.second.inputStream()) }

                bucketUploadService.uploadDocuments(alleDokumenter = dokumenter)

                val tsmSkanningmetadata =
                    ocrService.tryPapirsykmeldingOcr(dokumenter.toMap(), loggingMeta)?.let {
                        toSykmeldingParseResult(
                            it,
                            sykmeldingId,
                            pasient,
                            datoOpprettet,
                            journalpostId,
                            loggingMeta,
                            pasient.aktorId,
                            pasient.fnr,
                            Source.TSM,
                        )
                    } ?: SykmeldingParseResult.NoResult(Source.TSM)

                if (tsmSkanningmetadata is SykmeldingParseResult.OK) {
                    log.info("Ocr result received for $journalpostId")
                    val ocrAvvik =
                        scanningOcrFile?.let {
                            sammenlignOcr(
                                journalpostId,
                                it,
                                tsmSkanningmetadata.skanningmetadata,
                                loggingMeta,
                            )
                        }
                }

                val scanningResult =
                    scanningOcrFile?.let {
                        toSykmeldingParseResult(
                            it,
                            sykmeldingId,
                            pasient,
                            datoOpprettet,
                            journalpostId,
                            loggingMeta,
                            pasient.aktorId,
                            pasient.fnr,
                            Source.SCANNING,
                        )
                    } ?: SykmeldingParseResult.NoResult(Source.SCANNING)

                logResult(tsmSkanningmetadata, journalpostId)
                logResult(scanningResult, journalpostId)

                // OK fra TSM > OK fra skanning > feil fra skanning > feil/NoResult fra TSM
                val result =
                    tsmSkanningmetadata as? SykmeldingParseResult.OK
                        ?: scanningResult as? SykmeldingParseResult.OK
                        ?: scanningResult as? SykmeldingParseResult.Failed
                        ?: tsmSkanningmetadata

                when (result) {
                    is SykmeldingParseResult.OK -> {
                        log.info(
                            "Sykmelding mappet til internt format uten feil fra ${result.source}",
                            fields(loggingMeta),
                        )

                        log.info("Validerer sykmelding mot regler, {}", fields(loggingMeta))
                        val validationResult =
                            nyRegelClient.valider(result.receivedSykmelding, sykmeldingId)
                        log.info(
                            "Resultat: {}, {}, {}",
                            StructuredArguments.keyValue(
                                "ruleStatus",
                                validationResult.status.name,
                            ),
                            StructuredArguments.keyValue(
                                "ruleHits",
                                validationResult.ruleHits.joinToString(", ", "(", ")") {
                                    it.ruleName
                                },
                            ),
                            fields(loggingMeta),
                        )

                        if (
                            validationResult.status == Status.MANUAL_PROCESSING ||
                                requireManuellBehandling(result.receivedSykmelding)
                        ) {
                            FEILARSAK.labels("manuell_behandling").inc()
                            manuellBehandling(
                                journalpostId = journalpostId,
                                fnr = pasient.fnr,
                                aktorId = pasient.aktorId,
                                dokumentInfoId = dokumentInfoId,
                                datoOpprettet = datoOpprettet,
                                temaEndret = temaEndret,
                                loggingMeta = loggingMeta,
                                sykmeldingId = sykmeldingId,
                                sykmelder = result.sykmelder,
                                ocrFil = result.skanningmetadata,
                                kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                                smregistreringTopic = smregistreringTopic,
                            )
                        } else if (validationResult.status == Status.OK) {
                            handleOk(
                                kafkaReceivedSykmeldingProducer = kafkaReceivedSykmeldingProducer,
                                okSykmeldingTopic = okSykmeldingTopic,
                                receivedSykmelding = result.receivedSykmelding,
                                sykmeldingId = result.receivedSykmelding.sykmelding.id,
                                dokArkivClient = dokArkivClient,
                                journalpostid = journalpostId,
                                loggingMeta = loggingMeta,
                            )
                        } else {
                            throw IllegalStateException(
                                "Ukjent status: ${validationResult.status}. Papirsykmeldinger kan kun ha en av to typer statuser: OK eller MANUAL_PROCESSING"
                            )
                        }

                        log.info("Sykmelding håndtert automatisk {}", fields(loggingMeta))
                        PAPIRSM_MAPPET.labels("ok").inc()
                        return
                    }

                    is SykmeldingParseResult.Failed -> {
                        // Brukes til forhåndsutfylling i smregistrering i fallback under
                        skanningmetadata = result.skanningmetadata
                        sykmelder = result.sykmelder
                        throw result.exception
                    }
                    is SykmeldingParseResult.NoResult -> {
                        log.info("NoResult for journalpostId $journalpostId")
                    }
                }
            } catch (e: SafNotFoundException) {
                log.warn("Noe gikk galt ved uthenting av dokument: ${e.message}")
            } catch (e: TssException) {
                log.error("feil fra SMTSS")
                throw e
            } catch (e: Exception) {
                tellFeilArsak(e.message)
                PAPIRSM_MAPPET.labels("feil").inc()
                log.warn(
                    "Noe gikk galt ved mapping fra OCR til sykmeldingsformat, ${fields(loggingMeta)}",
                    e,
                )
            }
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
                ocrFil = skanningmetadata,
                kafkaproducerPapirSmRegistering = kafkaproducerPapirSmRegistering,
                smregistreringTopic = smregistreringTopic,
            )
        }
    }

    private fun logResult(tsmSkanningmetadata: SykmeldingParseResult, journalpostId: String) {
        val ocrResult =
            when (val result = tsmSkanningmetadata) {
                is SykmeldingParseResult.OK -> {
                    log.info("Ocr resultfor $journalpostId, from source ${result.source}")
                    result
                }

                is SykmeldingParseResult.Failed -> {
                    log.warn("ocr failed for $journalpostId, from source ${result.source}")
                    result
                }

                is SykmeldingParseResult.NoResult -> {
                    log.info("No ocr result for $journalpostId, from source ${result.source}")
                    result
                }
            }

        SYKMELDING_OCR_RESULT.labels(ocrResult.source.name, ocrResult.status.name).inc()
    }

    private suspend fun toSykmeldingParseResult(
        skanningmetadata: Skanningmetadata,
        sykmeldingId: String,
        pasient: PdlPerson,
        datoOpprettet: LocalDateTime?,
        journalpostId: String,
        loggingMeta: LoggingMeta,
        aktorId: String,
        fnr: String,
        source: SykmeldingParseResult.Source,
    ): SykmeldingParseResult {
        var sykmelder: Sykmelder? = null
        return try {
            val hentetSykmelder =
                hentSykmelder(
                    ocrFil = skanningmetadata,
                    sykmeldingId = sykmeldingId,
                    loggingMeta = loggingMeta,
                )
            sykmelder = hentetSykmelder
            val receivedSykmelding =
                toReceivedSykmelding(
                    skanningmetadata,
                    hentetSykmelder,
                    sykmeldingId,
                    pasient = pasient,
                    datoOpprettet = datoOpprettet,
                    journalpostId = journalpostId,
                    loggingMeta = loggingMeta,
                    pasientAktorId = aktorId,
                    pasientFnr = fnr,
                )
            SykmeldingParseResult.OK(skanningmetadata, receivedSykmelding, hentetSykmelder, source)
        } catch (e: TssException) {
            // smtss er nede, kastes videre slik at meldingen prøves på nytt
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SykmeldingParseResult.Failed(
                skanningmetadata = skanningmetadata,
                sykmelder = sykmelder,
                errorMessage = "could not map to sykmelding from ocr",
                exception = e,
                source = source,
            )
        }
    }

    private suspend fun toReceivedSykmelding(
        ocr: Skanningmetadata,
        sykmelder: Sykmelder,
        sykmeldingId: String,
        loggingMeta: LoggingMeta,
        pasient: PdlPerson,
        journalpostId: String,
        pasientAktorId: String,
        pasientFnr: String,
        datoOpprettet: LocalDateTime?,
    ): ReceivedSykmelding {
        val tssId =
            smtssClient.findBestTssInfotrygdId(sykmelder.fnr, "ukjent", loggingMeta, sykmeldingId)

        val fellesformat =
            mapOcrFilTilFellesformat(
                skanningmetadata = ocr,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                loggingMeta = loggingMeta,
                pdlPerson = pasient,
                journalpostId = journalpostId,
                icpc2BDiagnoser = icpc2BDiagnoser,
            )

        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
        val msgHead = fellesformat.get<XMLMsgHead>()

        val sykmelding =
            healthInformation.toSykmelding(
                sykmeldingId = sykmeldingId,
                pasientAktoerId = pasientAktorId,
                legeAktoerId = sykmelder.aktorId,
                msgId = sykmeldingId,
                signaturDato = getLocalDateTime(msgHead.msgInfo.genDate),
            )

        val receivedSykmelding =
            ReceivedSykmelding(
                sykmelding = sykmelding,
                personNrPasient = pasientFnr,
                tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                personNrLege = sykmelder.fnr,
                legeHprNr = sykmelder.hprNummer,
                legeHelsepersonellkategori = sykmelder.godkjenninger.getHelsepersonellKategori(),
                navLogId = sykmeldingId,
                msgId = sykmeldingId,
                legekontorOrgNr = null,
                legekontorOrgName = "",
                legekontorHerId = null,
                legekontorReshId = null,
                mottattDato = (datoOpprettet ?: getLocalDateTime(msgHead.msgInfo.genDate)),
                rulesetVersion = healthInformation.regelSettVersjon,
                fellesformat = fellesformatMarshaller.toString(fellesformat),
                tssid = tssId ?: "",
                merknader = null,
                partnerreferanse = null,
                vedlegg = emptyList(),
                utenlandskSykmelding = null,
            )
        return receivedSykmelding
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
            val papirSmRegistering =
                mapOcrFilTilPapirSmRegistrering(
                    journalpostId = journalpostId,
                    oppgaveId = oppgave.oppgaver.firstOrNull()?.id?.toString(),
                    fnr = fnr,
                    aktorId = aktorId,
                    dokumentInfoId = dokumentInfoId,
                    datoOpprettet = datoOpprettet?.atOffset(ZoneOffset.UTC),
                    sykmeldingId = sykmeldingId,
                    sykmelder = sykmelder,
                    ocrFil = ocrFil,
                )
            sendPapirSmRegistreringToKafka(
                kafkaproducerPapirSmRegistering,
                smregistreringTopic,
                papirSmRegistering,
                loggingMeta,
            )
        } else {
            log.info("duplikat oppgave ${fields(loggingMeta)}")
        }
    }

    private fun requireManuellBehandling(receivedSykmelding: ReceivedSykmelding): Boolean {
        val minFom =
            receivedSykmelding.sykmelding.perioder.minByOrNull { periode -> periode.fom }?.fom
        val maxTom =
            receivedSykmelding.sykmelding.perioder
                .maxByOrNull { periode: Periode -> periode.tom }
                ?.tom
        val today = LocalDate.now()

        val limit = 90

        if (ChronoUnit.DAYS.between(minFom, maxTom) > limit) {
            log.info(
                "Sender oppgave til manuell kontroll fordi avstanden mellom fom og tom er større enn $limit"
            )
            return true
        } else if (ChronoUnit.DAYS.between(minFom, today) > limit) {
            log.info(
                "Sender oppgave til manuell kontroll fordi avstanden mellom fom og dagens dato er større enn $limit"
            )
            return true
        }

        return false
    }

    suspend fun hentSykmelder(
        ocrFil: Skanningmetadata,
        sykmeldingId: String,
        loggingMeta: LoggingMeta,
    ): Sykmelder {
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
            log.warn(
                "Fant ikke aktorId til behandler for HPR {} {}",
                hprNummer,
                fields(loggingMeta),
            )
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
        if (
            ocr.sykemeldinger.medisinskVurdering.hovedDiagnose.isEmpty() &&
                ocr.sykemeldinger.medisinskVurdering.bidiagnose.isEmpty() &&
                ocr.sykemeldinger.medisinskVurdering.annenFraversArsak.isNullOrEmpty()
        ) {
            log.info(
                "Papirsykmelding inneholder ikke hovedDiagnose, biDiagnose eller annenFraversArsak {}",
                fields(loggingMeta),
            )
            PAPIRSM_MOTTATT_MED_OCR_UTEN_INNHOLD.inc()
        }
    }

    internal fun sammenlignOcr(
        journalpostId: String,
        ocrFil: Skanningmetadata,
        tsmSkanningmetadata: Skanningmetadata,
        loggingMeta: LoggingMeta,
    ): List<OcrAvvik> {
        return try {
            val avvik = sammenlignSkanningmetadata(ocrFil, tsmSkanningmetadata)
            if (avvik.isEmpty()) {
                OCR_SAMMENLIGNING.labels("lik").inc()
                log.info("Tsm-ocr er lik ocr-fil {}", fields(loggingMeta))
            } else {
                OCR_SAMMENLIGNING.labels("ulik").inc()
                val felter = avvik.map { it.felt }.distinct()
                felter.forEach { OCR_SAMMENLIGNING_AVVIK_FELT.labels(it).inc() }
                log.info(
                    "Tsm-ocr avviker fra ocr-fil i ${felter.size} felt(er): {}, {}",
                    StructuredArguments.keyValue("ocrAvvik", felter.joinToString(", ")),
                    fields(loggingMeta),
                )
                securelog.warn("Ocr avvik fra ocr $avvik")
            }
            avvik
        } catch (e: Exception) {
            OCR_SAMMENLIGNING.labels("feil").inc()
            log.warn("Klarte ikke sammenligne tsm-ocr med ocr-fil {}", fields(loggingMeta), e)
            emptyList()
        }
    }

    private fun tellFeilArsak(feilmelding: String?) {
        val label =
            feilmelding?.let {
                if (it.startsWith("Kunne ikke hente fnr for hpr")) {
                    "fant_ikke_behandler_hpr"
                } else if (it.startsWith("Mangler informasjon om behandler")) {
                    "ocr_mangler_behandler"
                } else if (it.startsWith("HPR-nummer mangler")) {
                    "ocr_mangler_hpr"
                } else if (
                    it.startsWith("skanningmetadata.sykemeldinger.pasient must not be null")
                ) {
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

fun List<Godkjenning>.getHelsepersonellKategori(): String? =
    when {
        find { it.helsepersonellkategori?.verdi == "LE" && it.helsepersonellkategori.aktiv } !=
            null -> "LE"

        find { it.helsepersonellkategori?.verdi == "TL" && it.helsepersonellkategori.aktiv } !=
            null -> "TL"

        find { it.helsepersonellkategori?.verdi == "MT" && it.helsepersonellkategori.aktiv } !=
            null -> "MT"

        find { it.helsepersonellkategori?.verdi == "KI" && it.helsepersonellkategori.aktiv } !=
            null -> "KI"

        find { it.helsepersonellkategori?.verdi == "FT" && it.helsepersonellkategori.aktiv } !=
            null -> "FT"

        else -> {
            val verdi = firstOrNull()?.helsepersonellkategori?.verdi
            log.warn(
                "Signerende behandler har ikke en helsepersonellkategori($verdi) vi kjenner igjen"
            )
            verdi
        }
    }
