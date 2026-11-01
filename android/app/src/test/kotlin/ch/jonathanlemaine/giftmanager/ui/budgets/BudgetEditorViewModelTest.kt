package ch.jonathanlemaine.giftmanager.ui.budgets

import ch.jonathanlemaine.giftmanager.core.budgets.Budget
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.FakeBudgetRepository
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftsEnv
import ch.jonathanlemaine.giftmanager.ui.gifts.giftsEnv
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
class BudgetEditorViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val lea = uuid(401)
    private val zoe = uuid(403)
    private val christmas = uuid(501)
    private val past = uuid(502)

    /** Le 15 octobre 2026 : Noël est à venir, l'événement du 1er septembre est passé. */
    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    private val existing = Budget(uuid(951), lea, "Léa", past, "Événement 2", BigDecimal("1200.50"), "EUR", BigDecimal.ZERO)

    private fun TestScope.vm(env: GiftsEnv, repo: FakeBudgetRepository, mode: BudgetEditorMode = BudgetEditorMode.Create) =
        BudgetEditorViewModel(mode, env.session, repo, clock)

    @Test fun createStartsEmptyInTheProfileCurrency() = runTest {
        val state = vm(giftsEnv(), FakeBudgetRepository()).uiState.value
        assertFalse(state.isEditing)
        assertEquals("CHF", state.currency)
        assertEquals("", state.amountText)
        assertFalse(state.hasScope)
        assertFalse(state.canSave)
        assertFalse(state.showsInvalid)
        assertEquals(listOf(lea, zoe), state.children.map { it.id })
        // Seuls les événements à venir.
        assertEquals(listOf(christmas), state.events.map { it.id })
    }

    @Test fun profileWithoutCurrencyFallsBackToEuro() = runTest {
        val env = giftsEnv { f.profiles.me = null }
        assertEquals("EUR", vm(env, FakeBudgetRepository()).uiState.value.currency)
    }

    @Test fun editStartsFromTheBudgetAndKeepsItsPastEvent() = runTest {
        val state = vm(giftsEnv(), FakeBudgetRepository(), BudgetEditorMode.Edit(existing)).uiState.value
        assertTrue(state.isEditing)
        assertEquals(lea, state.childId)
        assertEquals(past, state.eventId)
        assertEquals("EUR", state.currency)
        assertEquals("1 200,50", state.amountText)
        assertEquals(0, BigDecimal("1200.50").compareTo(state.amount))
        // Événements à venir, plus celui du budget édité.
        assertEquals(listOf(christmas, past), state.events.map { it.id })
        assertTrue(state.canSave)
    }

    @Test fun amountRules() = runTest {
        val vm = vm(giftsEnv(), FakeBudgetRepository())
        vm.onChildChange(lea)
        fun amount(text: String): BigDecimal? {
            vm.onAmountChange(text)
            return vm.uiState.value.amount
        }
        assertEquals(0, BigDecimal("200.00").compareTo(amount("200")))
        assertEquals(0, BigDecimal("199.90").compareTo(amount("199,9")))
        assertEquals(0, BigDecimal("1200.00").compareTo(amount("1'200")))
        // Trois chiffres après le dernier séparateur : c'est un séparateur de milliers (même règle qu'iOS).
        assertEquals(0, BigDecimal("12345.00").compareTo(amount("12.345")))
        assertEquals(2, vm.uiState.value.amount?.scale())
        assertNull(amount("0"))
        assertNull(amount("0,00"))
        assertNull(amount(""))
        assertNull(amount("abc"))
        assertNull(amount("99999999,99"))
        assertEquals(0, BigDecimal("99999999.00").compareTo(amount("99999999")))
        vm.onAmountChange("abc")
        assertTrue(vm.uiState.value.showsInvalid)
        assertFalse(vm.uiState.value.canSave)
        vm.onAmountChange("")
        assertFalse(vm.uiState.value.showsInvalid)
    }

    @Test fun savingNeedsAScopeAndAnAmount() = runTest {
        val repo = FakeBudgetRepository()
        val vm = vm(giftsEnv(), repo)
        vm.onAmountChange("100")
        vm.save()
        assertTrue(repo.saved.isEmpty())
        vm.onEventChange(christmas)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test fun createSavesAndFinishes() = runTest {
        val env = giftsEnv()
        val repo = FakeBudgetRepository()
        val vm = vm(env, repo)
        vm.onChildChange(zoe)
        vm.onAmountChange("150,5")
        vm.onCurrencyChange("GBP")
        val revision = env.revision
        vm.save()
        assertEquals(listOf<Any?>(zoe, null, BigDecimal("150.50"), "GBP"), repo.saved.single())
        assertTrue(repo.deleted.isEmpty())
        assertTrue(vm.uiState.value.finished)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(revision + 1, env.revision)
    }

    @Test fun editRemovesTheOldBudgetWhenTheServerCreatedAnotherOne() = runTest {
        val repo = FakeBudgetRepository().apply { nextId = uuid(960) }
        val vm = vm(giftsEnv(), repo, BudgetEditorMode.Edit(existing))
        vm.onCurrencyChange("CHF")
        vm.save()
        assertEquals(listOf(existing.id), repo.deleted)
        assertTrue(vm.uiState.value.finished)
    }

    @Test fun editKeepsTheBudgetWhenTheServerUpdatedIt() = runTest {
        val repo = FakeBudgetRepository().apply { nextId = existing.id }
        val vm = vm(giftsEnv(), repo, BudgetEditorMode.Edit(existing))
        vm.onAmountChange("300")
        vm.save()
        assertEquals(listOf<Any?>(lea, past, BigDecimal("300.00"), "EUR"), repo.saved.single())
        assertTrue(repo.deleted.isEmpty())
        assertTrue(vm.uiState.value.finished)
    }

    @Test fun saveFailureKeepsTheFormOpen() = runTest {
        val env = giftsEnv()
        val repo = FakeBudgetRepository().apply { failure = IOException("réseau") }
        val vm = vm(env, repo)
        vm.onChildChange(lea)
        vm.onAmountChange("100")
        val revision = env.revision
        vm.save()
        val state = vm.uiState.value
        assertEquals(GiftError.Network, state.error)
        assertFalse(state.finished)
        assertFalse(state.isSaving)
        assertEquals(revision, env.revision)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test fun deleteAsksForConfirmation() = runTest {
        val env = giftsEnv()
        val repo = FakeBudgetRepository()
        val vm = vm(env, repo, BudgetEditorMode.Edit(existing))
        vm.requestDelete()
        assertTrue(vm.uiState.value.confirmDelete)
        vm.cancelDelete()
        assertFalse(vm.uiState.value.confirmDelete)
        assertTrue(repo.deleted.isEmpty())
        vm.requestDelete()
        val revision = env.revision
        vm.delete()
        assertEquals(listOf(existing.id), repo.deleted)
        assertTrue(vm.uiState.value.finished)
        assertFalse(vm.uiState.value.confirmDelete)
        assertEquals(revision + 1, env.revision)
    }

    @Test fun createCannotBeDeleted() = runTest {
        val repo = FakeBudgetRepository()
        val vm = vm(giftsEnv(), repo)
        vm.requestDelete()
        assertFalse(vm.uiState.value.confirmDelete)
        vm.delete()
        assertTrue(repo.deleted.isEmpty())
        assertFalse(vm.uiState.value.finished)
    }

    @Test fun deleteFailureReportsTheError() = runTest {
        val repo = FakeBudgetRepository().apply { failure = IOException("réseau") }
        val vm = vm(giftsEnv(), repo, BudgetEditorMode.Edit(existing))
        vm.delete()
        assertEquals(GiftError.Network, vm.uiState.value.error)
        assertFalse(vm.uiState.value.finished)
    }

}
