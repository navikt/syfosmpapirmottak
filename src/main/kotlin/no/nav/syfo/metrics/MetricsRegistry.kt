package no.nav.syfo.metrics

import io.prometheus.client.Counter

const val NAMESPACE = "syfosmpapirmottak"

val INCOMING_MESSAGE_COUNTER: Counter = Counter.build()
        .namespace(NAMESPACE)
        .name("incoming_message_count")
        .help("Counts the number of incoming messages")
        .register()

val OPPRETT_OPPGAVE_COUNTER: Counter = Counter.Builder()
        .namespace(NAMESPACE)
        .name("opprett_oppgave_counter")
        .help("Registers a counter for each oppgave that is created")
        .register()