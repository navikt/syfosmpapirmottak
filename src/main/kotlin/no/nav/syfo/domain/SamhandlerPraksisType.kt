package no.nav.syfo.domain

enum class SamhandlerPraksisType(val kodeVerdi: String) {
    AUDIOPEDAGOG("AUDI"),
    BEHANDLINGSREISER_I_UTLANDET("BEUT"),
    BIDRAGSREISER("BIRE"),
    FASTLONNSTILSKUDD_FYSIOTERAPI("FAFY"),
    FASTLEGE("FALE"),
    FASTLONNET("FALO"),
    LIS1_LEGE("FATU"),
    FRITT_BEHANDLINGSVALG("FRBE"),
    FYSIOTERAPEUT_KOMMUNAL("FYKO"),
    MANUELLTERAPEUT("FYMT"),
    FYSIOTERAPEUT("FYNO"),
    PSYKOMOTORIKER("FYPM"),
    FYSIOTERAPEUT_RIDETERAPI("FYRT"),
    UTDANNINGSKANDIDAT_MANUELL_TERAPI("FYUM"),
    UTDANNINGSKANDIDAT_PSYKOMOTORIKK("FYUP"),
    UTDANNINGSKANDIDAT_FYSIO_ANNET("FYUV"),
    HELSESTASJON("HELS"),
    JORDMOR("JORD"),
    KIROPRAKTOR("KINO"),
    KURSSENTRA("KURS"),
    PRIVAT_LABORATORIUM_OG_RADIOLOGI("LARO"),
    LEGEVAKT_KOMMUNAL("LEKO"),
    LEGEVAKT("LEVA"),
    LOGOPED("LOGO"),
    MULTIDOSE("MULT"),
    OKSYGENLEVERANDOR("OKSY"),
    REHABILITERINGSINSTITUSJON("OPPT"),
    ORTOPTIST("ORTO"),
    PASIENTREISER("PASI"),
    NEVROPSYKOLOG("PSNE"),
    PSYKOLOG("PSNO"),
    UTDANNINGSKANDIDAT_PSYKOLOG("PSUT"),
    REGIONALT_HELSEFORETAK("RHFO"),
    SPESIALIST_ANESTESIOLOGI("SPAN"),
    SPESIALIST_BARNESYKDOMMER("SPBA"),
    SPESIALIST("SPES"),
    SPESIALIST_FYSIKALSK_MEDISIN_OG_REHABILITERING("SPFY"),
    SPESIALIST_GYNEKOLOGI("SPGY"),
    SPESIALIST_HUDLEGE("SPHU"),
    SPESIALIST_INDREMEDISIN("SPIN"),
    SPESIALIST_KIRURGI("SPKI"),
    SPESIALIST_NEVROLOGI("SPNE"),
    SPESIALIST_ONKOLOGI("SPOK"),
    SPESIALIST_OYELEGE("SPOL"),
    SPESIALIST_ORE_NESE_HALS("SPON"),
    SPESIALIST_PSYKIATRI("SPPS"),
    SPESIALIST_RADIOLOGI("SPRA"),
    SPESIALIST_REVMATOLOGI("SPRE"),
    POLIKLINIKK("SYKE"),
    SYKEPLEIER("SYPL"),
    FYLKESKOMMUNAL_TANNLEGE("TAFY"),
    FYLKESKOMMUNAL_KJEVEORTOPED("TAFK"),
    TANNLEGE_KJEVEORTOPED("TAKJ"),
    TANNLEGE("TANN"),
    TANNPLEIER("TAPL"),
    TANNPLEIER_OFFENTLIG("TAPO"),
    OFFENTLIG_TJENESTEPENSJONSLEVERANDOR("TPOF"),
    PRIVAT_TJENESTEPENSJONSLEVERANDOR("TPPR"),
    TJENESTEPENSJON_UTLAND("TPUT"),
    UTEN_REFUSJONSRETT("URRE"),
    UTDANNINGSKANDIDAT("UTKA"),
}
