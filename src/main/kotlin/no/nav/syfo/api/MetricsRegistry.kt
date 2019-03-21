package no.nav.syfo.api

import io.prometheus.client.Summary

const val NAMESPACE = "syfosmpapirmottak"

val NETWORK_CALL_SUMMARY: Summary = Summary.Builder().namespace(NAMESPACE).name("network_call_summary")
        .labelNames("http_endpoint").help("Summary for networked call times").register()