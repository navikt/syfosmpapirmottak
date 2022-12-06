package no.nav.syfo.utland

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo

class FiltreringTest : FunSpec({

    context("Test av aldersfiltrering med etter 1995-filter") {
        test("Bruker født i 1995 gir true med etter 1995-filter") {
            trefferAldersfilter("15069588888", Filter.ETTER1995) shouldBeEqualTo true
        }
        test("Bruker født i 2001 gir true med etter 1995-filter") {
            trefferAldersfilter("15060188888", Filter.ETTER1995) shouldBeEqualTo true
        }
        test("Bruker født i 1994 gir false med etter 1995-filter") {
            trefferAldersfilter("15069488888", Filter.ETTER1995) shouldBeEqualTo false
        }
    }

    context("Test av aldersfiltrering med etter 1990-filter") {
        test("Bruker født i 1990 gir true med etter 1990-filter") {
            trefferAldersfilter("15069088888", Filter.ETTER1990) shouldBeEqualTo true
        }
        test("Bruker født i 2001 gir true med etter 1990-filter") {
            trefferAldersfilter("15060188888", Filter.ETTER1990) shouldBeEqualTo true
        }
        test("Bruker født i 1989 gir false med etter 1990-filter") {
            trefferAldersfilter("15068988888", Filter.ETTER1990) shouldBeEqualTo false
        }
    }

    context("Test av aldersfiltrering med etter 1980-filter") {
        test("Bruker født i 1985 gir true med etter 1980-filter") {
            trefferAldersfilter("15068588888", Filter.ETTER1980) shouldBeEqualTo true
        }
        test("Bruker født i 2001 gir true med etter 1980-filter") {
            trefferAldersfilter("15060188888", Filter.ETTER1980) shouldBeEqualTo true
        }
        test("Bruker født i 1979 gir false med etter 1990-filter") {
            trefferAldersfilter("15067988888", Filter.ETTER1980) shouldBeEqualTo false
        }
    }
})
