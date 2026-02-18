package no.nav.syfo.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.UnleashContext
import io.getunleash.util.UnleashConfig
import org.slf4j.LoggerFactory

class Unleash(unleashConfig: UnleashConfig, private val unleashContext: UnleashContext) {
    private val unleash: Unleash = DefaultUnleash(unleashConfig)

    companion object {
        private val log = LoggerFactory.getLogger(Unleash::class.java)
        private const val OPPRETT_EGENERKLARING_OPPGAVE = "OPPRETT_EGENERKLARING_OPPGAVE"
    }

    fun shouldOpprettOppgaveFromEgenerklaring(): Boolean {
        return unleash.isEnabled(OPPRETT_EGENERKLARING_OPPGAVE, unleashContext, false)
    }
}
