package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.itemLink
import ch.jonathanlemaine.giftmanager.data.wishItem
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinPotViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun dec(text: String) = BigDecimal(text).stripTrailingZeros()

    /** Cadeau de Zoé (autre foyer), prix 10.00 CHF par lien. */
    private fun TestScope.vm(env: GiftsEnv, n: Int = 1) =
        JoinPotViewModel(uuid(700 + n), env.session, env.items, env.pots, Fixtures.clock).also { runCurrent() }

    private suspend fun TestScope.env(item: WishItem = wishItem(1, childN = 3), links: Boolean = true) =
        giftsEnv {
            items.items += item
            if (links) items.itemLinks += itemLink(1, 1)
        }

    @Test fun newPotStartsEmptyWithDefaultCurrencyFromTheLink() = runTest {
        val vm = vm(env())
        val s = vm.uiState.value
        assertFalse(s.isLoading)
        assertEquals("", s.amountText)
        assertEquals("CHF", s.currency)
        assertNull(s.lockedCurrency)
        assertFalse(s.isUpdate)
        assertEquals(dec("10"), s.remainingForMe?.stripTrailingZeros())
        assertEquals("Zoé", s.childName)
        assertFalse(s.canSave)
    }

    @Test fun profileCurrencyWhenNoLinkHasAPrice() = runTest {
        val env = env(links = false)
        env.f.profiles.me = env.f.profiles.me?.copy(currency = "EUR")
        env.f.session.refreshAll()
        val vm = vm(env)
        assertEquals("EUR", vm.uiState.value.currency)
    }

    @Test fun existingPotLocksItsCurrencyAndStartsFromMyShare() = runTest {
        val item = wishItem(1, childN = 3, myContribution = BigDecimal("40"))
            .copy(potTotal = BigDecimal("120"), potCurrency = "EUR", potCount = 3)
        val env = env(item, links = false)
        env.items.itemLinks += itemLink(1, 1).copy(currency = "EUR", price = BigDecimal("200"))
        val vm = vm(env)
        val s = vm.uiState.value
        assertEquals("EUR", s.lockedCurrency)
        assertEquals("EUR", s.currency)
        assertTrue(s.isUpdate)
        assertEquals("40", s.amountText)
        assertEquals(dec("40"), s.amount?.stripTrailingZeros())
        // Reste 80 + ma part 40.
        assertEquals(dec("120"), s.remainingForMe?.stripTrailingZeros())
        vm.onCurrencyChange("USD")
        assertEquals("EUR", vm.uiState.value.currency)
    }

    @Test fun chosenCurrencyChangesTheReference() = runTest {
        val env = env()
        env.items.itemLinks += itemLink(2, 1).copy(currency = "EUR", price = BigDecimal("12"))
        val vm = vm(env)
        vm.onCurrencyChange("EUR")
        assertEquals("EUR", vm.uiState.value.currency)
        assertEquals(dec("12"), vm.uiState.value.remainingForMe?.stripTrailingZeros())
        vm.onCurrencyChange("USD")
        assertNull(vm.uiState.value.remainingForMe)
    }

    @Test fun amountParsingAndInvalidFlag() = runTest {
        val vm = vm(env())
        vm.onAmountChange("12,50")
        assertEquals(dec("12.5"), vm.uiState.value.amount?.stripTrailingZeros())
        assertFalse(vm.uiState.value.showsInvalid)
        assertTrue(vm.uiState.value.canSave)
        vm.onAmountChange("abc")
        assertNull(vm.uiState.value.amount)
        assertTrue(vm.uiState.value.showsInvalid)
        vm.onAmountChange("0")
        assertTrue(vm.uiState.value.showsInvalid)
        vm.onAmountChange("  ")
        assertFalse(vm.uiState.value.showsInvalid)
    }

    @Test fun suggestionFillsTheField() = runTest {
        val vm = vm(env())
        vm.onSuggestion(BigDecimal("12.50"))
        assertEquals("12,5", vm.uiState.value.amountText)
        assertEquals(dec("12.5"), vm.uiState.value.amount?.stripTrailingZeros())
    }

    @Test fun saveJoinsRefreshesAndFinishes() = runTest {
        val env = env()
        val vm = vm(env)
        val before = env.revision
        vm.onAmountChange("25")
        vm.save()
        runCurrent()
        assertEquals(1, env.pots.joined.size)
        val (itemId, amount, currency) = env.pots.joined.single()
        assertEquals(uuid(701), itemId)
        assertEquals(dec("25"), amount.stripTrailingZeros())
        assertEquals("CHF", currency)
        assertEquals(before + 1, env.revision)
        assertTrue(vm.uiState.value.finished)
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.error)
    }

    @Test fun saveWithoutValidAmountDoesNothing() = runTest {
        val env = env()
        val vm = vm(env)
        vm.save()
        vm.onAmountChange("x")
        vm.save()
        assertTrue(env.pots.joined.isEmpty())
    }

    @Test fun currencyMismatchIsTranslated() = runTest {
        val env = env()
        env.pots.failure = Exception("CURRENCY_MISMATCH")
        val vm = vm(env)
        val before = env.revision
        vm.onAmountChange("25")
        vm.save()
        assertEquals(GiftError.CurrencyMismatch, vm.uiState.value.error)
        assertFalse(vm.uiState.value.finished)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(before, env.revision)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test fun missingItemReportsNotFound() = runTest {
        val env = giftsEnv()
        val vm = vm(env, 9)
        assertEquals(GiftError.NotFound, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        vm.onAmountChange("5")
        vm.save()
        assertTrue(env.pots.joined.isEmpty())
    }
}
