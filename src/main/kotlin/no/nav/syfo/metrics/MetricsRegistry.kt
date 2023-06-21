package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Summary

const val NAMESPACE = "syfosmpapirmottak"

val REQUEST_TIME: Summary =
    Summary.build()
        .namespace(NAMESPACE)
        .name("request_time_ms")
        .help("Request time in milliseconds.")
        .register()

val PAPIRSM_MOTTATT: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("mottatt_papirsykmelding_count")
        .help("Antall mottatte papirsykmeldinger")
        .register()

val ENDRET_PAPIRSM_MOTTATT: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("mottatt_endretpapirsykmelding_count")
        .help("Antall mottatte papirsykmeldinger, tema endret")
        .register()

val PAPIRSM_MOTTATT_NORGE: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("mottatt_norsk_papirsykmelding_count")
        .help("Antall mottatte norske papirsykmeldinger")
        .register()

val PAPIRSM_MOTTATT_UTLAND: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("mottatt_papirsykmelding_utland_count")
        .help("Antall mottatte utenlandske papirsykmeldinger")
        .register()

val PAPIRSM_MOTTATT_UTEN_BRUKER: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("mottatt_papirsm_uten_bruker_count")
        .help("Antall mottatte papirsykmeldinger der bruker mangler")
        .register()

val PAPIRSM_MOTTATT_UTEN_OCR: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("mottatt_papirsm_uten_ocr_count")
        .help("Antall mottatte papirsykmeldinger der OCR-fil mangler")
        .register()

val PAPIRSM_MOTTATT_MED_OCR_UTEN_INNHOLD: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("mottatt_papirsm_med_tom_ocr_count")
        .help("Antall mottatte papirsykmeldinger der OCR-fil er til stede, men bare FNR er utfylt")
        .register()

val PAPIRSM_OPPGAVE: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("oppgave_papirsykmelding_count")
        .help("Antall oppgaver laget på papirsykmeldinger")
        .register()

val PAPIRSM_FORDELINGSOPPGAVE: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("fordelingsoppgave_papirsykmelding_count")
        .help("Antall fordelingsoppgaver laget på papirsykmeldinger")
        .register()

val PAPIRSM_HENTDOK_FEIL: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("hentdok_feil_papirsykmelding_count")
        .help("Antall dokumenter vi ikke kunne hente fra Saf")
        .register()

val PAPIRSM_MAPPET: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("papirsykmelding_mappet_count")
        .labelNames("status")
        .help("Antall sykmeldinger som mappes")
        .register()

val SAMHANDLERPRAKSIS_NOT_FOUND_COUNTER: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("samhandlerpraksis_not_found_counter")
        .help("Counts the number of cases where samhandler is not found")
        .register()

val SAMHANDLERPRAKSIS_FOUND_COUNTER: Counter =
    Counter.build()
        .labelNames("samhandlertypekode")
        .namespace(NAMESPACE)
        .name("samhandlerpraksis_found_counter")
        .help("Counts the number of cases where samhandler is found")
        .register()

val FEILARSAK: Counter =
    Counter.build()
        .labelNames("cause")
        .namespace(NAMESPACE)
        .name("mapping_feilet_counter")
        .help("Hvorfor mapping feiler")
        .register()

val SYK_DIG_OPPGAVER: Counter =
    Counter.build()
        .namespace(NAMESPACE)
        .name("syk_dig_count")
        .help("Antall sykmeldinger som er sendt til syk-dig")
        .register()
