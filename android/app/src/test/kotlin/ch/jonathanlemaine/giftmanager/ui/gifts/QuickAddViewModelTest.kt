package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.testChild
import ch.jonathanlemaine.giftmanager.core.session.testEvent
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun myListsGetWishesAndOthersGetIdeas() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady()
        val lea = testChild(1, name = "Léa")
        val moi = testChild(2, name = "Moi", adult = true)
        val zoe = testChild(3, householdN = 2, name = "Zoé")
        f.childRepo.all += listOf(lea, moi, zoe)
        f.eventRepo.all += listOf(testEvent(1, "2026-12-25", kind = GiftEventKind.CHRISTMAS), testEvent(2, "2026-11-02"))
        f.session.start()
        runCurrent()

        val state = QuickAddViewModel(f.session, Fixtures.clock).uiState.value
        assertEquals(listOf(lea, moi), state.myLists.map { it.child })
        assertTrue(state.myLists.all { it.kind == WishKind.WISH })
        assertEquals(listOf(zoe), state.otherLists.map { it.child })
        assertTrue(state.otherLists.all { it.kind == WishKind.IDEA })
        // Le prochain Noël prime sur un événement plus proche.
        assertEquals(uuid(501), state.defaultEventId)
    }

    @Test fun emptyFamilyHasNothingToPick() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady()
        f.session.start()
        runCurrent()
        val state = QuickAddViewModel(f.session, Fixtures.clock).uiState.value
        assertTrue(state.isEmpty)
        assertNull(state.defaultEventId)
    }
}
