package no.nav.syfo

enum class SpmId(val spmId: String, val spmTekst: String, val restriksjon: Restriksjonskode) {
    SpmId6_2_1("6.2.1", "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon.", Restriksjonskode.RESTRICTED_FOR_EMPLOYER),
    SpmId6_2_2("6.2.2", "Hvordan påvirker sykdommen arbeidsevnen?", Restriksjonskode.RESTRICTED_FOR_EMPLOYER),
    SpmId6_2_3("6.2.3", "Har behandlingen frem til nå bedret arbeidsevnen?", Restriksjonskode.RESTRICTED_FOR_EMPLOYER),
    SpmId6_2_4("6.2.4", "Beskriv pågående og planlagt henvisning,utredning og/eller behandling.", Restriksjonskode.RESTRICTED_FOR_EMPLOYER)
}
