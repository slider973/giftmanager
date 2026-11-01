package ch.jonathanlemaine.giftmanager.ui.main

import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLock
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.SessionPhase
import ch.jonathanlemaine.giftmanager.core.session.group
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun inviteIsConfirmedThenJoined() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady(1)
        f.families.joinable["ABC123"] = group(2)
        f.session.start()
        runCurrent()
        val vm = MainViewModel(f.session, ChildModeLock())
        assertNull(vm.pendingInviteCode.value)
        f.session.handleDeepLink("famillecadeaux://join/abc123")
        assertEquals("ABC123", vm.pendingInviteCode.value)
        vm.confirmInvite()
        runCurrent()
        assertNull(vm.pendingInviteCode.value)
        assertEquals("Famille 2", f.session.state.value.currentGroup?.name)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
        assertNull(vm.error.value)
    }

    @Test fun dismissingForgetsTheCode() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady(1)
        f.session.start()
        runCurrent()
        val vm = MainViewModel(f.session, ChildModeLock())
        f.session.handleDeepLink("famillecadeaux://join/ABC123")
        vm.dismissInvite()
        assertNull(vm.pendingInviteCode.value)
        assertEquals("Famille 1", f.session.state.value.currentGroup?.name)
    }

    @Test fun badCodeReportsAnError() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady(1)
        f.session.start()
        runCurrent()
        val vm = MainViewModel(f.session, ChildModeLock())
        f.session.handleDeepLink("famillecadeaux://join/BAD000")
        vm.confirmInvite()
        runCurrent()
        assertEquals(GiftError.InvalidInvite, vm.error.value)
        vm.dismissError()
        assertNull(vm.error.value)
    }

    @Test fun followsTheChildModeLock() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady(1)
        f.session.start()
        runCurrent()
        val lock = ChildModeLock()
        val vm = MainViewModel(f.session, lock)
        assertFalse(vm.childModeActive.value)
        lock.activate()
        assertTrue(vm.childModeActive.value)
        // L'invitation reste en attente pendant le mode enfant, pour s'afficher à la sortie.
        f.session.handleDeepLink("famillecadeaux://join/ABC123")
        assertEquals("ABC123", vm.pendingInviteCode.value)
        lock.release()
        assertFalse(vm.childModeActive.value)
    }

    @Test fun sharedLinkIsConsumedOnce() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady(1)
        f.session.start()
        runCurrent()
        val vm = MainViewModel(f.session, ChildModeLock())
        f.session.handleSharedLink("https://shop.example/velo")
        assertEquals("https://shop.example/velo", vm.pendingSharedLink.value)
        vm.consumeSharedLink()
        assertNull(vm.pendingSharedLink.value)
        assertNull(f.session.pendingSharedLink.value)
    }

    @Test fun sharedLinkWaitsForChildModeToEnd() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady(1)
        f.session.start()
        runCurrent()
        val lock = ChildModeLock()
        val vm = MainViewModel(f.session, lock)
        lock.activate()
        f.session.handleSharedLink("https://shop.example/velo")
        f.session.handleShareWithoutLink()
        assertNull("rien devant l'enfant", vm.pendingSharedLink.value)
        assertFalse(vm.shareWithoutLink.value)
        lock.release()
        assertTrue(vm.shareWithoutLink.value)
    }

    @Test fun sharedLinkAppearsAfterChildModeEnds() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady(1)
        f.session.start()
        runCurrent()
        val lock = ChildModeLock()
        val vm = MainViewModel(f.session, lock)
        lock.activate()
        f.session.handleSharedLink("https://shop.example/velo")
        assertNull(vm.pendingSharedLink.value)
        lock.release()
        assertEquals("https://shop.example/velo", vm.pendingSharedLink.value)
    }
}
