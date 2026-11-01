package ch.jonathanlemaine.giftmanager.ui.purchases

import ch.jonathanlemaine.giftmanager.core.currency.ExchangeRates
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.FakePurchaseRepository
import ch.jonathanlemaine.giftmanager.data.itemLink
import ch.jonathanlemaine.giftmanager.data.myContribution
import ch.jonathanlemaine.giftmanager.data.myReservation
import ch.jonathanlemaine.giftmanager.data.thanksNote
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftsEnv
import ch.jonathanlemaine.giftmanager.ui.gifts.giftsEnv
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class PurchasesViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private class Setup(val env: GiftsEnv, val purchases: FakePurchaseRepository, val rates: MutableStateFlow<ExchangeRates?>)

    private fun plain(text: String?) = text?.replace(' ', ' ')?.replace(' ', ' ')

    // 1 EUR = 0,93 CHF ; le profil de test est en CHF.
    private val chfPerEur = ExchangeRates("EUR", "2026-10-02", mapOf("CHF" to BigDecimal("0.93")), Instant.EPOCH)

    private suspend fun TestScope.setup(configure: FakePurchaseRepository.(GiftsEnv) -> Unit = {}): Setup {
        val env = giftsEnv()
        val purchases = FakePurchaseRepository().also { it.configure(env) }
        return Setup(env, purchases, MutableStateFlow(null))
    }

    private fun TestScope.vm(s: Setup) =
        PurchasesViewModel(s.env.session, s.purchases, s.env.items, s.env.reservations, s.env.pots, s.rates, s.env.thanks).also { runCurrent() }

    @Test fun countsTheThanksReceived() = runTest {
        val s = setup()
        s.env.thanks.notes = listOf(thanksNote(1), thanksNote(2))
        val vm = vm(s)
        assertEquals(2, vm.uiState.value.thanksCount)
    }

    @Test fun aFailedThanksCountKeepsTheLastKnownWithoutAnError() = runTest {
        val s = setup()
        s.env.thanks.notes = listOf(thanksNote(1))
        val vm = vm(s)
        s.env.thanks.failure = IOException()
        vm.refresh()
        assertEquals(1, vm.uiState.value.thanksCount)
        assertNull(vm.uiState.value.error)
    }

    @Test fun loadsReservationsContributionsAndLinks() = runTest {
        val s = setup {
            reservations = listOf(myReservation(1, eventN = 1, eventDate = "2026-12-25"), myReservation(2, "Zoé", childN = 3))
            contributions = listOf(myContribution(3))
        }
        s.env.items.itemLinks += listOf(itemLink(1, 1), itemLink(2, 2))
        val vm = vm(s)
        val state = vm.uiState.value
        assertTrue(state.hasLoaded)
        assertEquals(2, state.reservations.size)
        assertEquals(1, state.contributions.size)
        assertEquals(listOf("Événement 1", "Sans événement"), state.groups.map { it.title })
        assertEquals(uuid(801), state.bestLink(uuid(701))?.id)
        // 10 + 10 CHF de liens + 25 CHF de ma part de cagnotte.
        assertEquals(0, BigDecimal("45").compareTo(state.totals.single().amount))
        assertEquals("CHF", state.totals.single().currency)
        assertEquals("CH", state.preferredCountry)
        assertEquals(2, state.remainingCount)
        assertFalse(state.isEmpty)
    }

    @Test fun emptyOnceLoaded() = runTest {
        val vm = vm(setup())
        assertTrue(vm.uiState.value.hasLoaded)
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test fun errorKeepsTheDataAndAlwaysMarksTheLoad() = runTest {
        val s = setup { reservations = listOf(myReservation(1)) }
        val vm = vm(s)
        s.purchases.failure = IOException("réseau")
        vm.refresh()
        val state = vm.uiState.value
        assertEquals(GiftError.Network, state.error)
        assertEquals(1, state.reservations.size)
        assertTrue(state.hasLoaded)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test fun firstLoadFailureStillMarksTheLoad() = runTest {
        val s = setup { failure = IOException("réseau") }
        val vm = vm(s)
        assertTrue(vm.uiState.value.hasLoaded)
        assertTrue(vm.uiState.value.reservations.isEmpty())
        assertEquals(GiftError.Network, vm.uiState.value.error)
    }

    @Test fun reloadsWhenItemsChange() = runTest {
        val s = setup { reservations = listOf(myReservation(1)) }
        val vm = vm(s)
        s.purchases.reservations = listOf(myReservation(1), myReservation(2))
        s.env.session.itemsChanged()
        runCurrent()
        assertEquals(2, vm.uiState.value.reservations.size)
    }

    @Test fun refreshReloads() = runTest {
        val s = setup { reservations = listOf(myReservation(1)) }
        val vm = vm(s)
        val before = s.purchases.calls
        s.purchases.reservations = emptyList()
        vm.refresh()
        assertEquals(before + 1, s.purchases.calls)
        assertTrue(vm.uiState.value.reservations.isEmpty())
    }

    @Test fun togglePurchasedFlipsTheStatusAndSignalsTheChange() = runTest {
        val s = setup()
        val vm = vm(s)
        val revision = s.env.revision
        vm.togglePurchased(myReservation(1, status = ReservationState.RESERVED))
        vm.togglePurchased(myReservation(1, status = ReservationState.PURCHASED))
        assertEquals(listOf("setPurchased(true)", "setPurchased(false)"), s.env.reservations.calls)
        assertEquals(revision + 2, s.env.revision)
    }

    @Test fun cancelReleasesTheItem() = runTest {
        val s = setup { reservations = listOf(myReservation(1)) }
        val vm = vm(s)
        val revision = s.env.revision
        s.purchases.reservations = emptyList()
        vm.cancel(myReservation(1))
        assertEquals(listOf("cancel"), s.env.reservations.calls)
        assertEquals(revision + 1, s.env.revision)
        assertTrue(vm.uiState.value.reservations.isEmpty())
    }

    @Test fun failedActionReportsTheErrorWithoutSignallingAChange() = runTest {
        val s = setup { reservations = listOf(myReservation(1)) }
        val vm = vm(s)
        val revision = s.env.revision
        s.env.reservations.failure = IOException("réseau")
        vm.cancel(myReservation(1))
        assertEquals(GiftError.Network, vm.uiState.value.error)
        assertEquals(revision, s.env.revision)
        assertFalse(vm.uiState.value.isWorking)
        assertEquals(1, vm.uiState.value.reservations.size)
    }

    @Test fun leavingAPotAsksForConfirmationFirst() = runTest {
        val s = setup { contributions = listOf(myContribution(3)) }
        val vm = vm(s)
        val pot = vm.uiState.value.contributions.single()
        vm.requestLeave(pot)
        assertEquals(pot, vm.uiState.value.leaving)
        assertTrue(s.env.pots.calls.isEmpty())
        vm.cancelLeave()
        assertNull(vm.uiState.value.leaving)
        vm.requestLeave(pot)
        val revision = s.env.revision
        vm.confirmLeave()
        assertEquals(listOf("leave"), s.env.pots.calls)
        assertNull(vm.uiState.value.leaving)
        assertEquals(revision + 1, s.env.revision)
        // Sans demande en attente, confirmer ne fait rien.
        vm.confirmLeave()
        assertEquals(listOf("leave"), s.env.pots.calls)
    }

    @Test fun convertedTotalAppearsOnlyWithRatesAndMixedCurrencies() = runTest {
        val s = setup {
            reservations = listOf(myReservation(1))
            contributions = listOf(myContribution(3, amount = "50", currency = "EUR"))
        }
        s.env.items.itemLinks += itemLink(1, 1) // 10 CHF
        val vm = vm(s)
        assertNull(vm.uiState.value.convertedTotalText)
        assertFalse(vm.uiState.value.hasConversion)
        s.rates.value = chfPerEur
        // 50 EUR = 46,50 CHF ; + 10 CHF = 56,50 CHF -> 57 (arrondi à l'unité dès 10).
        val total = plain(vm.uiState.value.convertedTotalText)!!
        assertTrue(total, total.startsWith("≈ ") && total.contains("57") && total.contains("CHF") && total.endsWith(" au total · indicatif"))
        assertTrue(vm.uiState.value.hasConversion)
        assertEquals("2 octobre", vm.uiState.value.rateDateText)
        assertTrue(vm.uiState.value.convertedTotalSpokenText!!.startsWith("environ "))
    }

    @Test fun approximationsFollowTheProfileCurrency() = runTest {
        val s = setup {
            reservations = listOf(myReservation(1))
            contributions = listOf(myContribution(3, amount = "93", currency = "EUR"))
        }
        s.env.items.itemLinks += itemLink(1, 1).copy(currency = "EUR", price = BigDecimal("100"))
        s.rates.value = chfPerEur
        val state = vm(s).uiState.value
        // 100 EUR = 93 CHF.
        val approx = plain(state.approxText(state.bestLink(uuid(701))))!!
        assertTrue(approx, approx.startsWith("≈ ") && approx.contains("93") && approx.contains("CHF"))
        assertNull(state.approxText(null))
        assertTrue(plain(state.approxText(state.contributions.single()))!!.contains("86"))
    }
}
