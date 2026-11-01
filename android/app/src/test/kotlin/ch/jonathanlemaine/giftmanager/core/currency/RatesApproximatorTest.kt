package ch.jonathanlemaine.giftmanager.core.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatesApproximatorTest {
    private val approximator = RatesApproximator(sampleRates())

    @Test fun hidesConversionForSameCurrencyOrUnknownProfile() {
        assertNull(approximator.approxText(dec("199"), "CHF", "CHF"))
        assertNull(approximator.approxText(dec("199"), "chf", "CHF"))
        assertNull(approximator.approxText(dec("199"), "CHF", null))
        assertNull(approximator.approxText(null, "CHF", "EUR"))
        assertNull(approximator.approxText(dec("199"), "CHF", "JPY"))
        // Devise absente d'un lien : on ne devine pas.
        assertNull(approximator.approxText(dec("100"), null, "CHF"))
        // 199 CHF / 0,93 = 213,98 € -> ≈ 214 €.
        assertEquals("≈ 214 €", approximator.approxText(dec("199"), "CHF", "EUR").plain())
        assertEquals("environ 214 €, conversion indicative", approximator.spokenText(dec("199"), "CHF", "EUR").plain())
    }

    @Test fun noRatesNoConversion() {
        val none = RatesApproximator(null)
        assertNull(none.approxText(dec("199"), "CHF", "EUR"))
        assertNull(none.convert(dec("199"), "CHF", "EUR"))
        assertNull(none.convertedTotal(listOf("CHF" to dec("93")), "EUR"))
    }

    @Test fun convertedTotal() {
        // 93 CHF + 50 EUR = 100 EUR + 50 EUR = 150 EUR.
        assertEquals(0, dec("150").compareTo(approximator.convertedTotal(listOf("CHF" to dec("93"), "EUR" to dec("50")), "EUR")))
        // Tout est déjà en euros : rien à convertir.
        assertNull(approximator.convertedTotal(listOf("EUR" to dec("50")), "EUR"))
        // Un taux manque : pas de total partiel.
        assertNull(approximator.convertedTotal(listOf("CHF" to dec("93"), "JPY" to dec("1000")), "EUR"))
        assertNull(approximator.convertedTotal(emptyList(), "EUR"))
        assertNull(approximator.convertedTotal(listOf("CHF" to dec("93")), null))
    }

    @Test fun hasConversion() {
        assertTrue(approximator.hasConversion(listOf(dec("10") to "CHF", null to "CHF"), "EUR"))
        assertFalse(approximator.hasConversion(listOf(dec("10") to "EUR", dec("5") to null, null to "CHF"), "EUR"))
        assertFalse(approximator.hasConversion(emptyList(), "EUR"))
    }
}
