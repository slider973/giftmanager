package ch.jonathanlemaine.giftmanager.ui

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.model.Profile
import ch.jonathanlemaine.giftmanager.data.GiftRepository
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import ch.jonathanlemaine.giftmanager.data.auth.FakeAuthRepository
import ch.jonathanlemaine.giftmanager.ui.home.HomeUiState
import ch.jonathanlemaine.giftmanager.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private class FakeRepository(
        var profile: Profile? = null,
        var failure: Exception? = null,
    ) : GiftRepository {

        override suspend fun currentProfile(): Profile? {
            failure?.let { throw it }
            return profile
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun signedInStateCarriesDisplayName() = runTest {
        val viewModel = HomeViewModel(FakeRepository(profile = Fixtures.decode("profile.json")), FakeAuthRepository())
        assertEquals(HomeUiState.SignedIn("Jonathan"), viewModel.uiState.value)
    }

    @Test fun noProfileMeansSignedOut() = runTest {
        val viewModel = HomeViewModel(FakeRepository(), FakeAuthRepository())
        assertEquals(HomeUiState.SignedOut, viewModel.uiState.value)
    }

    @Test fun repositoryFailureBecomesErrorState() = runTest {
        val viewModel = HomeViewModel(FakeRepository(failure = IllegalStateException("réseau")), FakeAuthRepository())
        assertEquals(HomeUiState.Error, viewModel.uiState.value)
    }

    @Test fun refreshRecoversFromError() = runTest {
        val repository = FakeRepository(failure = IllegalStateException("réseau"))
        val viewModel = HomeViewModel(repository, FakeAuthRepository())
        assertTrue(viewModel.uiState.value is HomeUiState.Error)
        repository.failure = null
        repository.profile = Fixtures.decode("profile.json")
        viewModel.refresh()
        assertEquals(HomeUiState.SignedIn("Jonathan"), viewModel.uiState.value)
    }

    @Test fun signOutFailureBecomesErrorState() = runTest {
        val auth = FakeAuthRepository().apply { signOutFailure = IllegalStateException("réseau") }
        val viewModel = HomeViewModel(FakeRepository(profile = Fixtures.decode("profile.json")), auth)
        viewModel.signOut()
        assertEquals(HomeUiState.Error, viewModel.uiState.value)
    }

    @Test fun signOutClearsSession() = runTest {
        val auth = FakeAuthRepository(AuthState.SignedIn(UUID.randomUUID()))
        val viewModel = HomeViewModel(FakeRepository(profile = Fixtures.decode("profile.json")), auth)
        viewModel.signOut()
        assertEquals(1, auth.signOutCalls)
        assertEquals(AuthState.SignedOut, auth.stateFlow.value)
        assertEquals(HomeUiState.SignedOut, viewModel.uiState.value)
    }
}
