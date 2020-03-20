package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.fail
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeLessThan
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KuhrSarClientSpek : Spek({

    val objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    describe("KuhrSarClient") {
        val samhandler: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_response.json").readBytes().toString(Charsets.UTF_8))

        it("Finner en aktiv samhandler praksis") {
            val match = findBestSamhandlerPraksis(samhandler, LoggingMeta("", "", ""))
                    ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldBeLessThan 50.0
        }

        it("Foretrekker samhandler praksisen med en matchende her id selv om navnet er likt") {
            val match = findBestSamhandlerPraksis(samhandler, LoggingMeta("", "", ""))
                    ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldEqual 100.0
            match.samhandlerPraksis.samh_praksis_id shouldEqual "1000456788"
        }

        it("Finner en samhandler praksis når navnet matcher 100%") {
            val match = findBestSamhandlerPraksis(samhandler, LoggingMeta("", "", ""))
                    ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldEqual 100.0
            match.samhandlerPraksis.samh_praksis_id shouldEqual "1000456789"
        }

        it("Finner en samhandler praksis når her iden ikke matcher") {
            val match = findBestSamhandlerPraksis(samhandler, LoggingMeta("", "", ""))
                    ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldEqual 100.0
            match.samhandlerPraksis.samh_praksis_id shouldEqual "1000456789"
        }

        it("Finner en samhandler som har navn på praksis når noen mangler navn") {
            val samhandlerMedNavn: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_response_falo.json").readBytes().toString(Charsets.UTF_8))
            val match = findBestSamhandlerPraksis(samhandlerMedNavn, LoggingMeta("", "", ""))
                    ?: fail("Unable to find samhandler praksis")
            match.samhandlerPraksis.samh_praksis_id shouldEqual "1000456788"
        }

        it("Finner en samhandler når det bare er inaktivte samhandlere") {
            val samhandlerMedNavn: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_response_inaktive.json").readBytes().toString(Charsets.UTF_8))

            val match = samhandlerMatchingPaaOrganisjonsNavn(samhandlerMedNavn, "Testlegesenteret")

            match?.samhandlerPraksis?.navn shouldEqual "Testlegesenteret - org nr"
        }
    }
})
