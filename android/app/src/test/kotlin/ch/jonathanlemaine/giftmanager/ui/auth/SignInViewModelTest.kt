package ch.jonathanlemaine.giftmanager.ui.auth

import ch.jonathanlemaine.giftmanager.data.auth.AuthError
import ch.jonathanlemaine.giftmanager.data.auth.FakeAuthRepository
import ch.jonathanlemaine.giftmanager.data.auth.fakeContext
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private val auth = FakeAuthRepository()
    private val viewModel = SignInViewModel(auth)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun failWith(error: Throwable, expected: SignInUiState) {
        auth.signInFailure = error
        viewModel.signIn(fakeContext())
        assertEquals(expected, viewModel.uiState.value)
    }

    @Test fun startsIdle() {
        assertEquals(SignInUiState.Idle, viewModel.uiState.value)
    }

    @Test fun successReturnsToIdle() = runTest {
        viewModel.signIn(fakeContext())
        assertEquals(1, auth.signInCalls)
        assertEquals(SignInUiState.Idle, viewModel.uiState.value)
    }

    @Test fun cancellationReturnsSilentlyToIdle() = runTest {
        failWith(AuthError.Cancelled(), SignInUiState.Idle)
    }

    @Test fun noGoogleAccount() = runTest {
        failWith(AuthError.NoGoogleAccount(), SignInUiState.Error(SignInUiState.Kind.NoGoogleAccount))
    }

    @Test fun network() = runTest {
        failWith(AuthError.Network(), SignInUiState.Error(SignInUiState.Kind.Network))
    }

    @Test fun notConfigured() = runTest {
        failWith(AuthError.NotConfigured(), SignInUiState.Error(SignInUiState.Kind.NotConfigured))
    }

    @Test fun googleUnavailable() = runTest {
        failWith(AuthError.GoogleUnavailable(), SignInUiState.Error(SignInUiState.Kind.GoogleUnavailable))
    }

    @Test fun failedAndUnknownErrorsAreGeneric() = runTest {
        failWith(AuthError.Failed(), SignInUiState.Error(SignInUiState.Kind.Generic))
        failWith(IllegalStateException("inattendu"), SignInUiState.Error(SignInUiState.Kind.Generic))
    }

    @Test fun dismissClearsError() = runTest {
        failWith(AuthError.Network(), SignInUiState.Error(SignInUiState.Kind.Network))
        viewModel.dismissMessage()
        assertEquals(SignInUiState.Idle, viewModel.uiState.value)
    }

    @Test fun dismissDoesNotInterruptProgress() = runTest {
        auth.signInGate = CompletableDeferred()
        viewModel.signIn(fakeContext())
        viewModel.dismissMessage()
        assertEquals(SignInUiState.InProgress, viewModel.uiState.value)
    }

    @Test fun secondCallWhileInProgressIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        auth.signInGate = gate
        viewModel.signIn(fakeContext())
        assertEquals(SignInUiState.InProgress, viewModel.uiState.value)
        viewModel.signIn(fakeContext())
        assertEquals(1, auth.signInCalls)
        gate.complete(Unit)
        assertEquals(SignInUiState.Idle, viewModel.uiState.value)
    }

    @Test fun canRetryAfterError() = runTest {
        failWith(AuthError.Network(), SignInUiState.Error(SignInUiState.Kind.Network))
        auth.signInFailure = null
        viewModel.signIn(fakeContext())
        assertEquals(2, auth.signInCalls)
        assertEquals(SignInUiState.Idle, viewModel.uiState.value)
    }

    @Test fun showErrorKeepsSupabaseMessageAndCanBeDismissed() = runTest {
        viewModel.showError("Invalid login credentials")
        assertEquals(SignInUiState.Error(SignInUiState.Kind.Generic, "Invalid login credentials"), viewModel.uiState.value)
        viewModel.dismissMessage()
        assertEquals(SignInUiState.Idle, viewModel.uiState.value)
    }
}
