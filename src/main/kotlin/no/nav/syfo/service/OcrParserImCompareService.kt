package no.nav.syfo.service

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import no.nav.helse.papirsykemelding.Skanningmetadata
import no.nav.syfo.metrics.OCR_SAMMENLIGNING_DOKUMENT
import no.nav.syfo.metrics.OCR_SAMMENLIGNING_FELT
import no.nav.syfo.model.OcrParserAktivitet
import no.nav.syfo.model.OcrParserSykmelding
import no.nav.syfo.securelog

/** Resultat av å sammenligne ett felt mellom ny og gammel OCR-tolkning. */
internal data class FeltSammenligning(
    val felt: String,
    val ny: String?,
    val gammel: String?,
    val lik: Boolean,
)

class OcrParserImCompareService {

    fun compare(
        nyttOcrResultat: OcrParserSykmelding,
        ironMountainOcrResultat: Skanningmetadata,
        sykmeldingId: String,
        journalpostId: String,
    ) {
        val felter = sammenlignFelter(nyttOcrResultat, ironMountainOcrResultat)

        // Metrics
        felter.forEach { felt ->
            OCR_SAMMENLIGNING_FELT.labels(felt.felt, if (felt.lik) "lik" else "avvik").inc()
        }
        OCR_SAMMENLIGNING_DOKUMENT.labels(if (felter.all { it.lik }) "alle_like" else "har_avvik")
            .inc()

        securelog.info(byggSammenligningsLogg(felter, sykmeldingId, journalpostId))
    }

    internal fun sammenlignFelter(
        nyttOcrResultat: OcrParserSykmelding,
        ironMountainOcrResultat: Skanningmetadata,
    ): List<FeltSammenligning> {
        val gammel = ironMountainOcrResultat.sykemeldinger
        val felter = mutableListOf<FeltSammenligning>()

        fun record(felt: String, ny: Any?, gammel: Any?, lik: Boolean = ny == gammel) {
            felter.add(FeltSammenligning(felt, ny?.toString(), gammel?.toString(), lik))
        }

        // Sykefravær
        record(
            "legemeldtFravaerStart",
            nyttOcrResultat.legemeldtFravaerStart,
            gammel.syketilfelleStartDato,
            parseDato(nyttOcrResultat.legemeldtFravaerStart) == gammel.syketilfelleStartDato,
        )

        // Pasient (gammel PasientType eksponerer kun fnr)
        record("pasient.fnr", nyttOcrResultat.pasient.fnr, gammel.pasient.fnr)

        // Arbeidsgiver
        record(
            "arbeidsgiver.navn",
            nyttOcrResultat.arbeidsgiver.navn,
            gammel.arbeidsgiver.navnArbeidsgiver,
        )
        record(
            "arbeidsgiver.stillingsprosent",
            nyttOcrResultat.arbeidsgiver.stillingsprosent,
            gammel.arbeidsgiver.stillingsprosent,
            nyttOcrResultat.arbeidsgiver.stillingsprosent ==
                gammel.arbeidsgiver.stillingsprosent?.toInt(),
        )
        record(
            "arbeidsgiver.flereArbeidsgivere",
            nyttOcrResultat.arbeidsgiver.flereArbeidsgivere,
            gammel.arbeidsgiver.harArbeidsgiver,
            nyttOcrResultat.arbeidsgiver.flereArbeidsgivere ==
                (gammel.arbeidsgiver.harArbeidsgiver?.lowercase()?.contains("flere") ?: false),
        )
        record(
            "arbeidsgiver.yrkeStilling",
            nyttOcrResultat.arbeidsgiver.yrkeStilling,
            gammel.arbeidsgiver.yrkesbetegnelse,
        )

        // Medisinsk vurdering / diagnose
        val hovedDiagnose = gammel.medisinskVurdering.hovedDiagnose.firstOrNull()
        record(
            "diagnose.hoveddiagnoseKode",
            nyttOcrResultat.diagnose.hoveddiagnoseKode,
            hovedDiagnose?.diagnosekode,
        )
        record(
            "diagnose.hoveddiagnoseKodesystem",
            nyttOcrResultat.diagnose.hoveddiagnoseKodesystem,
            hovedDiagnose?.diagnosekodeSystem,
        )
        record(
            "diagnose.hoveddiagnoseTekst",
            nyttOcrResultat.diagnose.hoveddiagnoseTekst,
            hovedDiagnose?.diagnose,
        )

        val gammelBidiagnoser = gammel.medisinskVurdering.bidiagnose ?: emptyList()
        val nyBidiagnoserStr =
            nyttOcrResultat.diagnose.bidiagnoser.joinToString("; ") {
                "${it.kode}/${it.kodesystem}/${it.tekst}"
            }
        val gammelBidiagnoserStr =
            gammelBidiagnoser.joinToString("; ") {
                "${it.diagnosekode}/${it.diagnosekodeSystem}/${it.diagnose}"
            }
        record(
            "diagnose.bidiagnoser",
            nyBidiagnoserStr.ifEmpty { null },
            gammelBidiagnoserStr.ifEmpty { null },
            nyttOcrResultat.diagnose.bidiagnoser.size == gammelBidiagnoser.size &&
                nyttOcrResultat.diagnose.bidiagnoser.zip(gammelBidiagnoser).all { (ny, gml) ->
                    ny.kode == gml.diagnosekode &&
                        ny.kodesystem == gml.diagnosekodeSystem &&
                        ny.tekst == gml.diagnose
                },
        )

        record(
            "diagnose.annenFravarsarsak",
            nyttOcrResultat.diagnose.annenFravarsarsak,
            gammel.medisinskVurdering.annenFraversArsak,
        )
        record(
            "diagnose.yrkesskade",
            nyttOcrResultat.diagnose.yrkesskade,
            gammel.medisinskVurdering.isYrkesskade,
        )
        record(
            "diagnose.svangerskap",
            nyttOcrResultat.diagnose.svangerskap,
            gammel.medisinskVurdering.isSvangerskap,
        )
        record(
            "diagnose.skadedato",
            nyttOcrResultat.diagnose.skadedato,
            gammel.medisinskVurdering.yrkesskadedato,
            parseDato(nyttOcrResultat.diagnose.skadedato) ==
                gammel.medisinskVurdering.yrkesskadedato,
        )
        record(
            "diagnose.skjermingPaakrevet",
            nyttOcrResultat.diagnose.skjermingPaakrevet,
            gammel.medisinskVurdering.isSkjermesForPasient,
        )

        // Aktiviteter
        val nyIkkeMulig =
            nyttOcrResultat.aktiviteter
                .filterIsInstance<OcrParserAktivitet.IkkeMulig>()
                .firstOrNull()
        val gammelIkkeMulig = gammel.aktivitet?.aktivitetIkkeMulig
        if (nyIkkeMulig != null || gammelIkkeMulig != null) {
            record(
                "aktivitet.aktivitetIkkeMulig (tilstede)",
                nyIkkeMulig != null,
                gammelIkkeMulig != null,
            )
            record(
                "aktivitet.aktivitetIkkeMulig.fom",
                nyIkkeMulig?.fom,
                gammelIkkeMulig?.periodeFOMDato,
                parseDato(nyIkkeMulig?.fom) == gammelIkkeMulig?.periodeFOMDato,
            )
            record(
                "aktivitet.aktivitetIkkeMulig.tom",
                nyIkkeMulig?.tom,
                gammelIkkeMulig?.periodeTOMDato,
                parseDato(nyIkkeMulig?.tom) == gammelIkkeMulig?.periodeTOMDato,
            )
            record(
                "aktivitet.aktivitetIkkeMulig.medisinskArsak",
                nyIkkeMulig?.medisinskArsak,
                gammelIkkeMulig?.medisinskeArsaker != null,
                (nyIkkeMulig?.medisinskArsak ?: false) ==
                    (gammelIkkeMulig?.medisinskeArsaker != null),
            )
            record(
                "aktivitet.aktivitetIkkeMulig.arbeidsrelatertArsak",
                nyIkkeMulig?.arbeidsrelatertArsak,
                gammelIkkeMulig?.arbeidsplassen != null,
                (nyIkkeMulig?.arbeidsrelatertArsak ?: false) ==
                    (gammelIkkeMulig?.arbeidsplassen != null),
            )
        }

        val nyGradert =
            nyttOcrResultat.aktiviteter.filterIsInstance<OcrParserAktivitet.Gradert>().firstOrNull()
        val gammelGradert = gammel.aktivitet?.gradertSykmelding
        if (nyGradert != null || gammelGradert != null) {
            record("aktivitet.gradert (tilstede)", nyGradert != null, gammelGradert != null)
            record(
                "aktivitet.gradert.fom",
                nyGradert?.fom,
                gammelGradert?.periodeFOMDato,
                parseDato(nyGradert?.fom) == gammelGradert?.periodeFOMDato,
            )
            record(
                "aktivitet.gradert.tom",
                nyGradert?.tom,
                gammelGradert?.periodeTOMDato,
                parseDato(nyGradert?.tom) == gammelGradert?.periodeTOMDato,
            )
            record(
                "aktivitet.gradert.grad",
                nyGradert?.grad,
                gammelGradert?.sykmeldingsgrad,
                nyGradert?.grad == gammelGradert?.sykmeldingsgrad?.toIntOrNull(),
            )
            record(
                "aktivitet.gradert.reisetilskudd",
                nyGradert?.reisetilskudd,
                gammelGradert?.isReisetilskudd,
                (nyGradert?.reisetilskudd ?: false) == (gammelGradert?.isReisetilskudd ?: false),
            )
        }

        val nyAvventende =
            nyttOcrResultat.aktiviteter
                .filterIsInstance<OcrParserAktivitet.Avventende>()
                .firstOrNull()
        val gammelAvventende = gammel.aktivitet?.avventendeSykmelding
        if (nyAvventende != null || gammelAvventende != null) {
            record(
                "aktivitet.avventende (tilstede)",
                nyAvventende != null,
                gammelAvventende != null,
            )
            record(
                "aktivitet.avventende.fom",
                nyAvventende?.fom,
                gammelAvventende?.periodeFOMDato,
                parseDato(nyAvventende?.fom) == gammelAvventende?.periodeFOMDato,
            )
            record(
                "aktivitet.avventende.tom",
                nyAvventende?.tom,
                gammelAvventende?.periodeTOMDato,
                parseDato(nyAvventende?.tom) == gammelAvventende?.periodeTOMDato,
            )
            // innspillTilArbeidsgiver ligg på AktivitetType-nivå i gammel modell
            record(
                "aktivitet.avventende.innspillTilArbeidsgiver",
                nyAvventende?.innspillTilArbeidsgiver,
                gammel.aktivitet?.innspillTilArbeidsgiver,
            )
        }

        val nyBehandlingsdager =
            nyttOcrResultat.aktiviteter
                .filterIsInstance<OcrParserAktivitet.Behandlingsdager>()
                .firstOrNull()
        val gammelBehandlingsdager = gammel.aktivitet?.behandlingsdager
        if (nyBehandlingsdager != null || gammelBehandlingsdager != null) {
            record(
                "aktivitet.behandlingsdager (tilstede)",
                nyBehandlingsdager != null,
                gammelBehandlingsdager != null,
            )
            record(
                "aktivitet.behandlingsdager.fom",
                nyBehandlingsdager?.fom,
                gammelBehandlingsdager?.periodeFOMDato,
                parseDato(nyBehandlingsdager?.fom) == gammelBehandlingsdager?.periodeFOMDato,
            )
            record(
                "aktivitet.behandlingsdager.tom",
                nyBehandlingsdager?.tom,
                gammelBehandlingsdager?.periodeTOMDato,
                parseDato(nyBehandlingsdager?.tom) == gammelBehandlingsdager?.periodeTOMDato,
            )
            record(
                "aktivitet.behandlingsdager.antallBehandlingsdager",
                nyBehandlingsdager?.antallBehandlingsdager,
                gammelBehandlingsdager?.antallBehandlingsdager,
                nyBehandlingsdager?.antallBehandlingsdager ==
                    gammelBehandlingsdager?.antallBehandlingsdager?.toInt(),
            )
        }

        val nyReisetilskudd =
            nyttOcrResultat.aktiviteter
                .filterIsInstance<OcrParserAktivitet.Reisetilskudd>()
                .firstOrNull()
        val gammelReisetilskudd = gammel.aktivitet?.reisetilskudd
        if (nyReisetilskudd != null || gammelReisetilskudd != null) {
            record(
                "aktivitet.reisetilskudd (tilstede)",
                nyReisetilskudd != null,
                gammelReisetilskudd != null,
            )
            record(
                "aktivitet.reisetilskudd.fom",
                nyReisetilskudd?.fom,
                gammelReisetilskudd?.periodeFOMDato,
                parseDato(nyReisetilskudd?.fom) == gammelReisetilskudd?.periodeFOMDato,
            )
            record(
                "aktivitet.reisetilskudd.tom",
                nyReisetilskudd?.tom,
                gammelReisetilskudd?.periodeTOMDato,
                parseDato(nyReisetilskudd?.tom) == gammelReisetilskudd?.periodeTOMDato,
            )
        }

        // Prognose
        record(
            "prognose.arbeidsforEtterPeriode",
            nyttOcrResultat.prognose.arbeidsforEtterPeriode,
            gammel.prognose?.friskmelding?.isArbeidsforEtterEndtPeriode,
            nyttOcrResultat.prognose.arbeidsforEtterPeriode ==
                (gammel.prognose?.friskmelding?.isArbeidsforEtterEndtPeriode ?: false),
        )

        // Tilleggsinformasjon
        record(
            "tilleggsinformasjon.bistandNavOnskes",
            nyttOcrResultat.tilleggsinformasjon.bistandNavOnskes,
            gammel.meldingTilNAV?.isBistandNAVUmiddelbart,
            nyttOcrResultat.tilleggsinformasjon.bistandNavOnskes ==
                (gammel.meldingTilNAV?.isBistandNAVUmiddelbart ?: false),
        )

        // Tilbakedatering
        record(
            "tilbakedatering.kontaktDato",
            nyttOcrResultat.tilbakedatering.kontaktDato,
            gammel.tilbakedatering?.tilbakeDato,
            parseDato(nyttOcrResultat.tilbakedatering.kontaktDato) ==
                gammel.tilbakedatering?.tilbakeDato,
        )
        record(
            "tilbakedatering.beskrivelse",
            nyttOcrResultat.tilbakedatering.beskrivelse,
            gammel.tilbakedatering?.tilbakebegrunnelse,
        )

        // Sykmelder (gammel: BehandlerType + kontaktMedPasient.behandletDato; navn finnes ikke i
        // gammel)
        record(
            "sykmelder.dato",
            nyttOcrResultat.sykmelder.dato,
            gammel.kontaktMedPasient?.behandletDato,
            parseDato(nyttOcrResultat.sykmelder.dato) == gammel.kontaktMedPasient?.behandletDato,
        )
        record(
            "sykmelder.hprNummer",
            nyttOcrResultat.sykmelder.hprNummer,
            gammel.behandler?.hpr,
            nyttOcrResultat.sykmelder.hprNummer == gammel.behandler?.hpr?.toString(),
        )
        record("sykmelder.adresse", nyttOcrResultat.sykmelder.adresse, gammel.behandler?.adresse)
        record(
            "sykmelder.telefon",
            nyttOcrResultat.sykmelder.telefon,
            gammel.behandler?.telefon,
            nyttOcrResultat.sykmelder.telefon == gammel.behandler?.telefon?.toString(),
        )

        return felter
    }

    internal fun byggSammenligningsLogg(
        felter: List<FeltSammenligning>,
        sykmeldingId: String,
        journalpostId: String,
    ): String {
        val like = felter.count { it.lik }
        val avvik = felter.size - like
        val medNull = felter.count { it.ny == null || it.gammel == null }
        val feltBredde = felter.maxOfOrNull { it.felt.length } ?: 0

        val sb = StringBuilder()
        sb.append("OCR-sammenligning sykmeldingId=")
            .append(sykmeldingId)
            .append(" journalpostId=")
            .append(journalpostId)
            .append(" — ")
            .append(felter.size)
            .append(" felt, ")
            .append(like)
            .append(" like, ")
            .append(avvik)
            .append(" avvik, ")
            .append(medNull)
            .append(" med null\n")

        felter.forEach { felt ->
            val tag =
                when {
                    felt.ny == null && felt.gammel == null -> " (begge null)"
                    felt.ny == null -> " (kun gammel)"
                    felt.gammel == null -> " (kun ny)"
                    else -> ""
                }
            sb.append("  ")
                .append((if (felt.lik) "LIK" else "AVVIK").padEnd(6))
                .append(felt.felt.padEnd(feltBredde + 2))
                .append("ny=")
                .append(felt.ny ?: "null")
                .append(" | gammel=")
                .append(felt.gammel ?: "null")
                .append(tag)
                .append("\n")
        }
        return sb.toString()
    }

    private fun parseDato(dato: String?): LocalDate? =
        if (dato.isNullOrBlank()) {
            null
        } else {
            try {
                LocalDate.parse(dato, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            } catch (e: DateTimeParseException) {
                null
            }
        }
}
