package ch.jonathanlemaine.giftmanager.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class CountriesTest {
    @Test fun fourteenCountriesAndFiveCurrencies() {
        assertEquals(14, Countries.all.size)
        assertEquals(5, Countries.currencies.size)
    }

    /** Parité avec `StoreCatalog.currencyByCountry` (iOS). */
    @Test fun currencyByCountryMatchesIos() {
        val expected = mapOf(
            "CH" to "CHF", "LI" to "CHF", "FR" to "EUR", "DE" to "EUR", "IT" to "EUR", "ES" to "EUR", "BE" to "EUR",
            "LU" to "EUR", "AT" to "EUR", "NL" to "EUR", "PT" to "EUR", "IE" to "EUR", "GB" to "GBP", "US" to "USD", "CA" to "CAD",
        )
        expected.forEach { (country, currency) -> assertEquals(country, currency, Countries.currencyFor(country)) }
        assertNull(Countries.currencyFor("JP"))
        assertNull(Countries.currencyFor(null))
    }

    @Test fun everyProposedCountryHasAProposedCurrency() {
        Countries.all.forEach { assert(Countries.currencyFor(it.code) in Countries.currencies) { it.code } }
    }

    @Test fun defaultCountryFollowsLocaleWhenSupported() {
        assertEquals("FR", Countries.defaultCountry(Locale.Builder().setLanguage("fr").setRegion("FR").build()))
        assertEquals("CA", Countries.defaultCountry(Locale.Builder().setLanguage("en").setRegion("CA").build()))
    }

    @Test fun defaultCountryFallsBackToSwitzerland() {
        assertEquals("CH", Countries.defaultCountry(Locale.Builder().setLanguage("ja").setRegion("JP").build()))
        assertEquals("CH", Countries.defaultCountry(Locale.ROOT))
    }
}
