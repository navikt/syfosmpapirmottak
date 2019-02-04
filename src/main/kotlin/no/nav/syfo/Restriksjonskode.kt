package no.nav.syfo

enum class Restriksjonskode(override val codeValue: String, override val text: String, override val oid: String = "2.16.578.1.12.4.1.1.8134") : Kodeverk {
    RESTRICTED_FOR_EMPLOYER("A", "Informasjonen skal ikke vises arbeidsgiver"),
    RESTRICTED_FOR_PATIENT("P", "Informasjonen skal ikke vises pasient"),
    RESTRICTED_FOR_NAV("N", "Informasjonen skal ikke vises NAV")
}
