package ch.jonathanlemaine.giftmanager.ui.childmode

import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLock
import ch.jonathanlemaine.giftmanager.core.childmode.ParentAuthenticator
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.wishItem
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftsEnv
import ch.jonathanlemaine.giftmanager.ui.gifts.giftsEnv
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChildModeViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeAuthenticator(var result: Boolean = true, var available: Boolean = true) : ParentAuthenticator {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null

        override fun isAvailable() = available

        override suspend fun authenticate(): Boolean {
            calls++
            gate?.await()
            return result
        }
    }

    private class Setup(val env: GiftsEnv, val lock: ChildModeLock, val auth: FakeAuthenticator, val vm: ChildModeViewModel)

    private val lea = uuid(401)

    private suspend fun TestScope.setup(eventId: UUID? = null, setup: GiftsEnv.() -> Unit = {}): Setup {
        val env = giftsEnv {
            items.items += listOf(
                wishItem(1, title = "Lego", status = null),
                wishItem(2, title = "Idée", kind = WishKind.IDEA, status = null),
                wishItem(3, title = "Déjà là", owned = true, status = null),
                wishItem(4, title = "Vélo", priority = 1, status = null, eventId = uuid(501)),
                wishItem(5, title = "Zoé", childN = 3),
            )
            setup()
        }
        val lock = ChildModeLock()
        val auth = FakeAuthenticator()
        val vm = ChildModeViewModel(lea, eventId, env.session, env.items, lock, auth).also { runCurrent() }
        return Setup(env, lock, auth, vm)
    }

    private val Setup.state get() = vm.uiState.value

    @Test fun loadsOnlyTheUnownedWishesOfTheChild() = runTest {
        val s = setup()
        assertFalse(s.state.isLoading)
        assertEquals(listOf("Lego", "Vélo"), s.state.items.map { it.title })
        assertEquals("Léa", s.state.child?.firstName)
        assertEquals(1, s.state.favoriteCount)
    }

    @Test fun asksOnlyForWishesOfTheListedEvent() = runTest {
        val s = setup(eventId = uuid(501))
        assertEquals(uuid(501), s.env.items.lastEventFilter)
    }

    @Test fun aFailedLoadShowsTheErrorAndAnEmptyList() = runTest {
        val s = setup { items.failure = IOException() }
        assertTrue(s.state.items.isEmpty())
        assertFalse(s.state.isLoading)
        assertTrue(s.state.error != null)
    }

    @Test fun lockIsActiveFromTheStartAndReleasedOnExit() = runTest {
        val s = setup()
        assertTrue(s.lock.active.value)
        s.vm.exit()
        assertFalse(s.lock.active.value)
        assertTrue(s.state.exited)
    }

    @Test fun lockIsReleasedWhenTheScreenIsDestroyedWithoutExit() = runTest {
        val s = setup()
        s.vm.javaClass.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(s.vm)
        assertFalse(s.lock.active.value)
    }

    @Test fun heartIsSavedOnTheWishAndCounted() = runTest {
        val s = setup()
        s.vm.toggleFavorite(uuid(701))
        assertTrue(s.state.items.first { it.id == uuid(701) }.isFavorite)
        assertEquals(2, s.state.favoriteCount)
        assertEquals(listOf(uuid(701) to true), s.env.items.priorities)
        assertTrue(s.state.didChange)
        assertTrue(s.state.saving.isEmpty())
        s.vm.toggleFavorite(uuid(701))
        assertFalse(s.state.items.first { it.id == uuid(701) }.isFavorite)
        assertEquals(listOf(uuid(701) to true, uuid(701) to false), s.env.items.priorities)
    }

    @Test fun aFailedSaveRestoresTheHeartAndFlagsTheFailure() = runTest {
        val s = setup()
        s.env.items.failure = IOException()
        s.vm.toggleFavorite(uuid(704))
        assertTrue(s.state.items.first { it.id == uuid(704) }.isFavorite)
        assertTrue(s.state.saveFailed)
        assertFalse(s.state.didChange)
        assertTrue(s.state.saving.isEmpty())
        s.vm.dismissSaveFailed()
        assertFalse(s.state.saveFailed)
    }

    @Test fun anUnknownWishIsIgnored() = runTest {
        val s = setup()
        s.vm.toggleFavorite(uuid(999))
        assertTrue(s.env.items.priorities.isEmpty())
        assertFalse(s.state.didChange)
    }

    @Test fun exitReloadsTheListsOnlyIfAHeartChanged() = runTest {
        val s = setup()
        val before = s.env.revision
        s.vm.exit()
        assertEquals(before, s.env.revision)

        val t = setup()
        t.vm.toggleFavorite(uuid(701))
        val revision = t.env.revision
        t.vm.exit()
        assertEquals(revision + 1, t.env.revision)
    }

    @Test fun exitNeedsTheAdultToBeVerified() = runTest {
        val s = setup()
        s.auth.result = false
        s.vm.requestExit()
        assertEquals(1, s.auth.calls)
        assertFalse(s.state.exited)
        assertTrue(s.lock.active.value)
        assertFalse(s.state.isAuthenticating)
        s.auth.result = true
        s.vm.requestExit()
        assertTrue(s.state.exited)
        assertFalse(s.lock.active.value)
    }

    @Test fun aSecondExitRequestIsIgnoredWhileVerifying() = runTest {
        val s = setup()
        s.auth.gate = CompletableDeferred()
        s.vm.requestExit()
        assertTrue(s.state.isAuthenticating)
        s.vm.requestExit()
        assertEquals(1, s.auth.calls)
        s.auth.gate!!.complete(Unit)
        runCurrent()
        assertTrue(s.state.exited)
    }

    @Test fun aLostVerificationCanBeCancelledAndRequestedAgain() = runTest {
        val s = setup()
        s.auth.gate = CompletableDeferred() // l'invite ne répondra jamais (rotation)
        s.vm.requestExit()
        assertTrue(s.state.isAuthenticating)
        s.vm.cancelAuthentication()
        runCurrent()
        assertFalse(s.state.isAuthenticating)
        assertFalse(s.state.exited)
        assertTrue(s.lock.active.value)
        s.auth.gate = null
        s.vm.requestExit()
        runCurrent()
        assertEquals(2, s.auth.calls)
        assertTrue(s.state.exited)
    }

    @Test fun exposesWhetherVerificationIsAvailable() = runTest {
        val s = setup()
        assertTrue(s.vm.isAuthenticationAvailable)
        s.auth.available = false
        assertFalse(s.vm.isAuthenticationAvailable)
    }
}
