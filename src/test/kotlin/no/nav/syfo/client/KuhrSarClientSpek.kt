package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KuhrSarClientSpek : Spek({

    val objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    describe("KuhrSarClient") {
        it("Finner en samhandler når det bare er inaktivte samhandlere") {
            val samhandlerMedNavn: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_response_inaktive.json").readBytes().toString(Charsets.UTF_8))

            val match = samhandlerMatchingPaaOrganisjonsNavn(samhandlerMedNavn, "Testlegesenteret")

            match?.samhandlerPraksis?.navn shouldEqual "Testlegesenteret - org nr"
        }
    }
})
