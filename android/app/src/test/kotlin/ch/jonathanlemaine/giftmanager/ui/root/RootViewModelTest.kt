package ch.jonathanlemaine.giftmanager.ui.root

import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.SessionPhase
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.profile
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun phaseToDestination() {
        fun dest(phase: SessionPhase, failed: Boolean = false) = SessionState(phase = phase, loadFailed = failed).toDestination()
        assertEquals(RootDestination.Loading, dest(SessionPhase.Launching))
        assertEquals(RootDestination.Onboarding, dest(SessionPhase.SignedOut))
        assertEquals(RootDestination.LoadFailed, dest(SessionPhase.SignedOut, failed = true))
        assertEquals(RootDestination.ProfileSetup, dest(SessionPhase.NeedsProfile))
        assertEquals(RootDestination.GroupSetup, dest(SessionPhase.NeedsGroup))
        assertEquals(RootDestination.Main, dest(SessionPhase.Ready))
    }

    @Test fun followsTheSessionEndToEnd() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.Loading)
        f.seedReady()
        f.session.start()
        val viewModel = RootViewModel(f.session)
        assertEquals(RootDestination.Loading, viewModel.destination.value)
        f.authRepo.stateFlow.value = AuthState.SignedOut
        runCurrent()
        assertEquals(RootDestination.Onboarding, viewModel.destination.value)
        f.authRepo.stateFlow.value = AuthState.SignedIn(uuid(1))
        runCurrent()
        assertEquals(RootDestination.Main, viewModel.destination.value)
        viewModel.signOut()
        runCurrent()
        assertEquals(RootDestination.Onboarding, viewModel.destination.value)
    }

    @Test fun retryAfterLoadFailure() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady()
        f.profiles.failure = IOException("réseau")
        f.session.start()
        runCurrent()
        val viewModel = RootViewModel(f.session)
        assertEquals(RootDestination.LoadFailed, viewModel.destination.value)
        f.profiles.failure = null
        viewModel.retry()
        runCurrent()
        assertEquals(RootDestination.Main, viewModel.destination.value)
    }

    @Test fun profileNotOnboardedGoesToSetup() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.profiles.me = profile(onboarded = false)
        f.session.start()
        runCurrent()
        assertEquals(RootDestination.ProfileSetup, RootViewModel(f.session).destination.value)
    }
}
