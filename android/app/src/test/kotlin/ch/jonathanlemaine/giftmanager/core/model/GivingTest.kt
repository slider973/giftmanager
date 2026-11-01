package ch.jonathanlemaine.giftmanager.core.model

import ch.jonathanlemaine.giftmanager.core.Fixtures
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GivingTest {
    private fun d(value: String) = BigDecimal(value)

    private fun assertSameAmount(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals("attendu $expected, obtenu $actual", 0, d(expected).compareTo(actual))
    }

    private fun link(price: String?, currency: String?) =
        ItemLink(UUID.randomUUID(), UUID.randomUUID(), "https://example.invalid", price = price?.let(::d), currency = currency)

    // region PotProgress

    @Test fun progressAgainstPriceInSameCurrency() {
        val links = listOf(link("230", "EUR"), link("200", "CHF"))
        val progress = PotProgress(d("150"), "CHF", PotProgress.target("CHF", links), 3)
        assertSameAmount("200", progress.target)
        assertEquals(0.75, progress.fraction!!, 0.0001)
        assertSameAmount("50", progress.remaining)
        assertFalse(progress.isComplete)
        assertEquals("3 participants", progress.participantsText)
    }

    @Test fun progressWithoutPriceHasNoGauge() {
        val target = PotProgress.target("CHF", listOf(link("99", "EUR")))
        val progress = PotProgress(d("60"), "CHF", target, 1)
        assertNull(progress.fraction)
        assertNull(progress.remaining)
        assertFalse(progress.isComplete)
        assertEquals("1 participant", progress.participantsText)
    }

    @Test fun overfundedProgressIsClamped() {
        val progress = PotProgress(d("260"), "CHF", d("200"), 4)
        assertEquals(1.0, progress.fraction!!, 0.0)
        assertSameAmount("0", progress.remaining)
        assertTrue(progress.isComplete)
    }

    @Test fun targetMatchesCurrencyCaseInsensitivelyAndIgnoresZeroPrices() {
        assertSameAmount("12", PotProgress.target("chf", listOf(link("0", "CHF"), link("12", "CHF"))))
        assertNull(PotProgress.target("CHF", listOf(link(null, "CHF"))))
    }

    @Test fun makeFromPotItemUsesLinks() {
        val item = Fixtures.decode<WishItem>("wish_item_pot.json")
        val progress = PotProgress.make(item, listOf(link("301.25", "CHF")))!!
        assertSameAmount("120.5", progress.collected)
        assertSameAmount("301.25", progress.target)
        assertEquals(3, progress.participants)
        assertEquals(0.4, progress.fraction!!, 0.0001)
    }

    @Test fun makeNeedsAPotWithParticipants() {
        val item = Fixtures.decode<WishItem>("wish_item_pot.json")
        assertNull(PotProgress.make(item.copy(potCount = 0), emptyList()))
        assertNull(PotProgress.make(item.copy(potTotal = null), emptyList()))
        assertNull(PotProgress.make(item.copy(potCurrency = null), emptyList()))
    }

    // endregion

    // region PotAmount

    @Test fun parsesCommonInputs() {
        assertSameAmount("25", PotAmount.parse("25"))
        assertSameAmount("12.5", PotAmount.parse("12,50"))
        assertSameAmount("12.5", PotAmount.parse(" 12.5 "))
        assertSameAmount("1000", PotAmount.parse("1'000"))
        assertSameAmount("1000", PotAmount.parse("1’000"))
        assertSameAmount("1000", PotAmount.parse("1 000"))
        assertSameAmount("10.56", PotAmount.parse("10,555"))
    }

    @Test fun rejectsInvalidInputs() {
        for (text in listOf("", "0", "-5", "abc", "1,2,3", "100000000", ".", "0,001")) {
            assertNull("« $text » devrait être refusé", PotAmount.parse(text))
        }
    }

    @Test fun suggestionsIncludeRemaining() {
        fun ints(list: List<BigDecimal>) = list.map { it.toInt() }
        assertEquals(listOf(10, 20, 50), ints(PotAmount.suggestions(null)))
        assertEquals(listOf(10, 20, 35), ints(PotAmount.suggestions(d("35"))))
        assertEquals(listOf(10, 20), ints(PotAmount.suggestions(d("20"))))
        assertEquals(listOf(10, 20, 50), ints(PotAmount.suggestions(d("0"))))
    }

    @Test fun editableTextKeepsShortForm() {
        assertEquals("12,5", PotAmount.editableText(d("12.50")))
        assertEquals("25", PotAmount.editableText(d("25.00")))
    }

    // endregion

    // region ReservationCounts

    @Test fun summaryTextPluralizes() {
        val counts = Fixtures.decode<List<ReservationCounts>>("reservation_counts.json")
        assertEquals("2 réservés · 1 acheté · 1 cagnotte", counts[0].summaryText)
        assertEquals("Rien de prévu pour l'instant", counts[1].summaryText)
        assertEquals("2 cadeaux réservés, 1 acheté, 1 en cagnotte", counts[0].accessibilityText)
        assertEquals("Aucun cadeau prévu pour l'instant", counts[1].accessibilityText)
    }

    @Test fun singularCounts() {
        val c = ReservationCounts(UUID.randomUUID(), 1, 0, 2)
        assertEquals("1 réservé · 2 cagnottes", c.summaryText)
        assertEquals("1 cadeau réservé, 2 en cagnotte", c.accessibilityText)
    }

    // endregion

    // region ReceiptRules

    private val clock = Fixtures.clock
    private fun day(y: Int, m: Int, d: Int) = DayDate(LocalDate.of(y, m, d))

    @Test fun hasStartedOnTheDayItself() {
        assertTrue(ReceiptRules.hasStarted(day(2026, 10, 15), clock))
        assertTrue(ReceiptRules.hasStarted(day(2026, 10, 14), clock))
        assertFalse(ReceiptRules.hasStarted(day(2026, 10, 16), clock))
        assertFalse(ReceiptRules.hasStarted(null, clock))
    }

    @Test fun markReceivedNeedsParentWishNotOwnedAndStartedEvent() {
        val past = day(2026, 10, 1)
        assertTrue(ReceiptRules.canMarkReceived(true, WishKind.WISH, false, past, clock))
        assertFalse(ReceiptRules.canMarkReceived(false, WishKind.WISH, false, past, clock))
        assertFalse(ReceiptRules.canMarkReceived(true, WishKind.IDEA, false, past, clock))
        assertFalse(ReceiptRules.canMarkReceived(true, WishKind.WISH, true, past, clock))
        assertFalse(ReceiptRules.canMarkReceived(true, WishKind.WISH, false, day(2026, 12, 25), clock))
    }

    @Test fun sendThanksNeedsOwnedWish() {
        val past = day(2026, 10, 1)
        assertTrue(ReceiptRules.canSendThanks(true, WishKind.WISH, true, past, clock))
        assertFalse(ReceiptRules.canSendThanks(true, WishKind.WISH, false, past, clock))
        assertFalse(ReceiptRules.canSendThanks(false, WishKind.WISH, true, past, clock))
        assertFalse(ReceiptRules.canSendThanks(true, WishKind.WISH, true, null, clock))
    }

    @Test fun donorSeesReceivedOnlyAfterTheParty() {
        assertTrue(ReceiptRules.isReceived(true, day(2026, 10, 1), clock))
        assertFalse(ReceiptRules.isReceived(true, day(2026, 12, 25), clock))
        assertFalse(ReceiptRules.isReceived(false, day(2026, 10, 1), clock))
    }

    // endregion
}
