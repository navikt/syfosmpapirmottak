package no.nav.syfo.mapping

import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sykSkanningMeta.SykemeldingerType

fun mappapirsykemeldingtosm2013(papirSykemelding: SykemeldingerType): HelseOpplysningerArbeidsuforhet = HelseOpplysningerArbeidsuforhet().apply {
    syketilfelleStartDato = papirSykemelding.syketilfelleStartDato
    pasient = HelseOpplysningerArbeidsuforhet.Pasient().apply {
        fodselsnummer = Ident().apply {
            id = papirSykemelding.pasient.fnr
            typeId = CV().apply {
                dn = "Fødselsnummer"
                s = "2.16.578.1.12.4.1.1.8116"
                v = "FNR"
            }
        }
    }
}