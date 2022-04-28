package no.nav.syfo.client

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import org.amshove.kluent.shouldBeEqualTo

class KuhrSarClientTest : FunSpec({
    val samhandlerPraksis = mockk<SamhandlerPraksis>(relaxed = true)

    context("KuhrSarClient") {
        test("Finner samhandlerpraksis for aktiv kiropraktor") {
            every { samhandlerPraksis.tss_ident } returns "123456789101112"
            every { samhandlerPraksis.samh_praksis_status_kode } returns "aktiv"
            val samhandlere = listOf(
                Samhandler(
                    "1",
                    "samhandlernavn",
                    "KI",
                    "",
                    "",
                    "", "", "", "", null, emptyList(), listOf(samhandlerPraksis)
                )
            )
            val hentetSamhandlerPraksis = findBestSamhandlerPraksis(samhandlere)
            hentetSamhandlerPraksis?.tss_ident shouldBeEqualTo "123456789101112"
        }

        test("Bruker aktiv samhandlerpraksis for kiropraktor med aktiv og inaktiv praksis") {
            val samhandlerPraksis2 = mockk<SamhandlerPraksis>(relaxed = true)
            every { samhandlerPraksis2.tss_ident } returns "2"
            every { samhandlerPraksis2.samh_praksis_status_kode } returns "inaktiv"
            every { samhandlerPraksis.tss_ident } returns "123456789101112"
            every { samhandlerPraksis.samh_praksis_status_kode } returns "aktiv"
            val samhandlere = getSamhandler("KI", samhandlerPraksis2, samhandlerPraksis)

            val hentetSamhandlerPraksis = findBestSamhandlerPraksis(samhandlere)

            hentetSamhandlerPraksis?.tss_ident shouldBeEqualTo "123456789101112"
        }

        test("Finner første aktive samhandlerprakis") {
            val samhandlerPraksis2 = mockk<SamhandlerPraksis>(relaxed = true)
            every { samhandlerPraksis2.tss_ident } returns "2"
            every { samhandlerPraksis2.samh_praksis_status_kode } returns "aktiv"
            val samhandlere = getSamhandler("LE", samhandlerPraksis2)

            val hentetSamhandlerPraksis = findBestSamhandlerPraksis(samhandlere)

            hentetSamhandlerPraksis?.tss_ident shouldBeEqualTo "2"
        }

        test("Bruker inaktiv samhandlerpraksis hvis ingen er aktive") {
            every { samhandlerPraksis.tss_ident } returns "1"
            every { samhandlerPraksis.samh_praksis_status_kode } returns "inaktiv"
            val samhandlere = getSamhandler("ANY", samhandlerPraksis)

            val hentetSamhandlerPraksis = findBestSamhandlerPraksis(samhandlere)

            hentetSamhandlerPraksis?.tss_ident shouldBeEqualTo "1"
        }
    }
})

private fun getSamhandler(type: String, vararg samhandlerPraksis: SamhandlerPraksis): MutableList<Samhandler> {
    return mutableListOf(
        Samhandler(
            "1",
            "samhandlernavn",
            type,
            "",
            "",
            "", "", "", "", null, emptyList(), samhandlerPraksis.toList()
        )
    )
}
