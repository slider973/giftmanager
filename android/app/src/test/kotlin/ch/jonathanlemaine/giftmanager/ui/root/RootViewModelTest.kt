package ch.jonathanlemaine.giftmanager.ui.root

import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import ch.jonathanlemaine.giftmanager.data.auth.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun exposesInitialAuthState() = runTest {
        val viewModel = RootViewModel(FakeAuthRepository(AuthState.Loading))
        assertEquals(AuthState.Loading, viewModel.state.value)
    }

    @Test fun followsSessionChanges() = runTest {
        val auth = FakeAuthRepository(AuthState.SignedOut)
        val viewModel = RootViewModel(auth)
        assertEquals(AuthState.SignedOut, viewModel.state.value)
        val id = UUID.randomUUID()
        auth.stateFlow.value = AuthState.SignedIn(id)
        assertEquals(AuthState.SignedIn(id), viewModel.state.value)
        auth.stateFlow.value = AuthState.SignedOut
        assertEquals(AuthState.SignedOut, viewModel.state.value)
    }
}
