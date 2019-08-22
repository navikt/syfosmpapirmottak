package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Summary

const val NAMESPACE = "syfosmpapirmottak"

val REQUEST_TIME: Summary = Summary.build()
        .namespace(NAMESPACE)
        .name("request_time_ms")
        .help("Request time in milliseconds.").register()

val PAPIRSM_MOTTATT: Counter = Counter.build()
    .namespace(NAMESPACE)
    .name("mottatt_papirsykmelding_count")
    .help("Antall mottatte papirsykmeldinger")
    .register()

val PAPIRSM_MOTTATT_UTEN_BRUKER: Counter = Counter.build()
    .namespace(NAMESPACE)
    .name("mottatt_papirsm_uten_bruker_count")
    .help("Antall mottatte papirsykmeldinger der bruker mangler")
    .register()

val PAPIRSM_OPPGAVE: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("oppgave_papirsykmelding_count")
        .help("Antall oppgaver laget på papirsykmeldinger")
        .register()

val PAPIRSM_FORDELINGSOPPGAVE: Counter = Counter.build()
    .namespace(NAMESPACE)
    .name("fordelingsoppgave_papirsykmelding_count")
    .help("Antall fordelingsoppgaver laget på papirsykmeldinger")
    .register()
