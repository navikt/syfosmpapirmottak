package no.nav.syfo.metrics

import io.prometheus.client.Summary

const val NAMESPACE = "syfosmpapirmottak"

val REQUEST_TIME: Summary = Summary.build()
        .namespace(NAMESPACE)
        .name("request_time_ms")
        .help("Request time in milliseconds.").register()
