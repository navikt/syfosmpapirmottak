package no.nav.syfo.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig

class Unleash(unleashConfig: UnleashConfig) {
    private val unleash: Unleash = DefaultUnleash(unleashConfig)

    companion object {
        private const val OPPRETT_EGENERKLARING_OPPGAVE = "OPPRETT_EGENERKLARING_OPPGAVE"
    }

    fun shouldOpprettOppgave(): Boolean {
        return unleash.isEnabled(OPPRETT_EGENERKLARING_OPPGAVE)
    }
}
