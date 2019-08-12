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
