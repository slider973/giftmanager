package ch.jonathanlemaine.giftmanager.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryFlagLogicTest {
    @Test fun emojiForValidCodes() {
        assertEquals("🇨🇭", CountryFlagLogic.emoji("CH"))
        assertEquals("🇫🇷", CountryFlagLogic.emoji(" fr "))
    }

    @Test fun ukIsMappedToGb() {
        assertEquals(CountryFlagLogic.emoji("GB"), CountryFlagLogic.emoji("uk"))
        assertEquals("Royaume-Uni", CountryFlagLogic.name("UK"))
    }

    @Test fun invalidCodesFallBackToGlobe() {
        listOf(null, "", "  ", "C", "CHE", "X1", "éé", "12").forEach { assertNull("$it", CountryFlagLogic.emoji(it)) }
    }

    @Test fun nameUsesCountriesList() {
        assertEquals("Suisse", CountryFlagLogic.name("ch"))
        assertEquals("France", CountryFlagLogic.name("FR"))
    }

    @Test fun nameFallsBackToLocaleThenUnknown() {
        assertEquals("Japon", CountryFlagLogic.name("JP"))
        assertEquals("Pays inconnu", CountryFlagLogic.name(null))
        assertEquals("Pays inconnu", CountryFlagLogic.name("X1"))
    }
}
