package ch.jonathanlemaine.giftmanager.ui.budgets

import ch.jonathanlemaine.giftmanager.core.budgets.Budget
import ch.jonathanlemaine.giftmanager.core.budgets.BudgetMath
import ch.jonathanlemaine.giftmanager.core.currency.ExchangeRates
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.FakeBudgetRepository
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftsEnv
import ch.jonathanlemaine.giftmanager.ui.gifts.giftsEnv
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
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
class BudgetsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val lea = uuid(401)
    private val zoe = uuid(403)
    private val christmas = uuid(501)
    private val past = uuid(502)

    // Profil de test en CHF ; 1 EUR = 0,93 CHF.
    private val rates = ExchangeRates("EUR", "2026-10-02", mapOf("CHF" to BigDecimal("0.93")), Instant.EPOCH)

    private fun budget(
        n: Int,
        child: UUID? = null,
        event: UUID? = null,
        spent: String = "0",
        amount: String = "200",
        currency: String = "CHF",
    ) = Budget(uuid(950 + n), child, child?.let { "Prénom $n" }, event, event?.let { "Titre $n" }, BigDecimal(amount), currency, BigDecimal(spent))

    private fun plain(text: String?) = text?.replace(' ', ' ')?.replace(' ', ' ')

    private class Setup(val env: GiftsEnv, val repo: FakeBudgetRepository, val rates: MutableStateFlow<ExchangeRates?>)

    private suspend fun TestScope.setup(vararg budgets: Budget): Setup {
        val env = giftsEnv()
        // Les événements de la session : Noël 2026 (25/12) et un événement passé (01/09).
        return Setup(env, FakeBudgetRepository().also { it.budgets = budgets.toList() }, MutableStateFlow(null))
    }

    private fun TestScope.vm(s: Setup) = BudgetsViewModel(s.env.session, s.repo, s.rates).also { runCurrent() }

    @Test fun loadsAndMarksTheLoad() = runTest {
        val vm = vm(setup(budget(1, child = lea, spent = "180")))
        val state = vm.uiState.value
        assertTrue(state.hasLoaded)
        assertFalse(state.showsInvitation)
        val row = state.rows.single()
        assertEquals("180 / 200 CHF", plain(row.gaugeText))
        assertEquals(BudgetMath.Level.NEARLY_REACHED, row.level)
        assertEquals("Plus que 20 CHF", plain(row.statusText))
        assertNull(row.approxText)
        assertNull(state.indicativeTotal)
    }

    @Test fun invitationWhenThereIsNoBudget() = runTest {
        val state = vm(setup()).uiState.value
        assertTrue(state.showsInvitation)
        assertFalse(state.hasConversion)
    }

    @Test fun statusPerLevel() = runTest {
        val state = vm(setup(budget(1, child = lea, spent = "50"), budget(2, child = zoe, spent = "245"))).uiState.value
        val byId = state.rows.associateBy { it.budget.id }
        assertEquals("Reste 150 CHF", plain(byId.getValue(uuid(951)).statusText))
        assertEquals("Dépassé de 45 CHF", plain(byId.getValue(uuid(952)).statusText))
        assertEquals(BudgetMath.Level.OVER, byId.getValue(uuid(952)).level)
    }

    @Test fun sortedByEventDateThenChildFirstName() = runTest {
        val s = setup(
            budget(1, child = zoe), // sans événement, Zoé
            budget(2, event = christmas), // Noël : 25/12/2026
            budget(3, child = lea), // sans événement, Léa
            budget(4, event = past), // événement passé : 01/09/2026, donc avant Noël
        )
        val ids = vm(s).uiState.value.rows.map { it.budget.id }
        // Date croissante ; sans date en dernier, par prénom (Léa avant Zoé : le prénom vient de la session).
        assertEquals(listOf(uuid(954), uuid(952), uuid(953), uuid(951)), ids)
    }

    @Test fun approximationInTheProfileCurrency() = runTest {
        val s = setup(budget(1, child = lea, spent = "93", amount = "186", currency = "EUR"))
        s.rates.value = rates
        val state = vm(s).uiState.value
        // 93 EUR = 86,49 CHF -> 86 ; 186 EUR = 172,98 CHF -> 173.
        assertEquals("≈ 86 / 173 CHF", plain(state.rows.single().approxText))
        assertTrue(state.hasConversion)
        assertEquals("2 octobre", state.rateDateText)
    }

    @Test fun indicativeTotalNeedsSeveralDisjointBudgetsWithKnownRates() = runTest {
        val mixed = arrayOf(
            budget(1, child = lea, spent = "50", amount = "100", currency = "CHF"),
            budget(2, child = zoe, spent = "93", amount = "186", currency = "EUR"),
        )
        // Sans taux : pas de total.
        val s = setup(*mixed)
        assertNull(vm(s).uiState.value.indicativeTotal)
        s.rates.value = rates
        val total = vm(s).uiState.value.indicativeTotal
        // 50 CHF + 86,49 CHF = 136,49 -> 136 ; 100 CHF + 172,98 CHF = 272,98 -> 273.
        val text = plain(total?.text)!!
        assertTrue(text, text.startsWith("≈ ") && text.contains("136") && text.contains("273") && text.endsWith("au total · indicatif"))
        assertTrue(plain(total?.spokenText)!!.startsWith("environ "))

        // Une seule devise : rien à convertir.
        val single = setup(budget(1, child = lea, currency = "CHF"), budget(2, child = zoe, currency = "CHF"))
        single.rates.value = rates
        assertNull(vm(single).uiState.value.indicativeTotal)
        // Un seul budget.
        val one = setup(budget(1, child = lea, currency = "EUR"))
        one.rates.value = rates
        assertNull(vm(one).uiState.value.indicativeTotal)
        // Budgets qui se recoupent (Léa, tous événements, et Noël, tous enfants) : un cadeau serait compté deux fois.
        val overlapping = setup(budget(1, child = lea, currency = "CHF"), budget(2, event = christmas, currency = "EUR"))
        overlapping.rates.value = rates
        assertNull(vm(overlapping).uiState.value.indicativeTotal)
        // Un taux manque : pas de total partiel.
        val unknown = setup(budget(1, child = lea, currency = "CHF"), budget(2, child = zoe, currency = "JPY"))
        unknown.rates.value = rates
        assertNull(vm(unknown).uiState.value.indicativeTotal)
    }

    @Test fun errorKeepsTheBudgetsAndMarksTheLoad() = runTest {
        val s = setup(budget(1, child = lea))
        val vm = vm(s)
        s.repo.failure = IOException("réseau")
        vm.refresh()
        assertEquals(GiftError.Network, vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.rows.size)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
        val failing = setup().also { it.repo.failure = IOException("réseau") }
        assertTrue(vm(failing).uiState.value.hasLoaded)
    }

    @Test fun reloadsWhenItemsChange() = runTest {
        val s = setup(budget(1, child = lea, spent = "10"))
        val vm = vm(s)
        s.repo.budgets = listOf(budget(1, child = lea, spent = "20"))
        s.env.session.itemsChanged()
        runCurrent()
        assertEquals("20 / 200 CHF", plain(vm.uiState.value.rows.single().gaugeText))
    }
}
