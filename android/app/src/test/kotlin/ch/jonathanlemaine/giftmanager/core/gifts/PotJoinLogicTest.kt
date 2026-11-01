package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.PotProgress
import ch.jonathanlemaine.giftmanager.core.session.uuid
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PotJoinLogicTest {
    private fun link(n: Int, price: String?, currency: String?) =
        ItemLink(uuid(n), uuid(1), "https://exemple.ch/$n", null, null, price?.let(::BigDecimal), currency)

    private fun dec(text: String) = BigDecimal(text).stripTrailingZeros()

    @Test fun lockedCurrencyWins() {
        assertEquals("EUR", PotJoinLogic.defaultCurrency("EUR", listOf(link(1, "10", "CHF")), "USD"))
    }

    @Test fun firstPricedLinkCurrencyComesNext() {
        val links = listOf(link(1, null, "GBP"), link(2, "10", null), link(3, "20", "EUR"), link(4, "5", "CHF"))
        assertEquals("EUR", PotJoinLogic.defaultCurrency(null, links, "USD"))
    }

    @Test fun profileCurrencyThenChf() {
        assertEquals("USD", PotJoinLogic.defaultCurrency(null, listOf(link(1, null, "EUR")), "USD"))
        assertEquals("CHF", PotJoinLogic.defaultCurrency(null, emptyList(), null))
    }

    @Test fun existingPotRemainingIncludesMyShare() {
        val progress = PotProgress(BigDecimal("120"), "CHF", BigDecimal("200"), 3)
        assertEquals(dec("120"), PotJoinLogic.remainingForMe(progress, BigDecimal("40"), "CHF", emptyList())?.stripTrailingZeros())
        assertEquals(dec("80"), PotJoinLogic.remainingForMe(progress, null, "CHF", emptyList())?.stripTrailingZeros())
    }

    @Test fun completePotRemainingIsMyShare() {
        val progress = PotProgress(BigDecimal("250"), "CHF", BigDecimal("200"), 3)
        assertEquals(dec("30"), PotJoinLogic.remainingForMe(progress, BigDecimal("30"), "CHF", emptyList())?.stripTrailingZeros())
    }

    @Test fun potWithoutTargetHasNoRemaining() {
        val progress = PotProgress(BigDecimal("60"), "EUR", null, 2)
        assertNull(PotJoinLogic.remainingForMe(progress, BigDecimal("10"), "EUR", emptyList()))
    }

    @Test fun newPotRemainingIsTheTargetInChosenCurrency() {
        val links = listOf(link(1, "199", "CHF"), link(2, "210", "EUR"))
        assertEquals(dec("210"), PotJoinLogic.remainingForMe(null, null, "EUR", links)?.stripTrailingZeros())
        assertNull(PotJoinLogic.remainingForMe(null, null, "USD", links))
    }

    @Test fun suggestionsEndWithTheRemainder() {
        val values = PotJoinLogic.suggestions(BigDecimal("35"))
        assertEquals(listOf(dec("10"), dec("20"), dec("35")), values.map { it.stripTrailingZeros() })
        assertEquals(listOf(dec("10"), dec("20"), dec("50")), PotJoinLogic.suggestions(null).map { it.stripTrailingZeros() })
    }
}
