package ch.jonathanlemaine.giftmanager.ui

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.model.Profile
import ch.jonathanlemaine.giftmanager.data.GiftRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private class FakeRepository(
        var profile: Profile? = null,
        var failure: Exception? = null,
    ) : GiftRepository {
        var signOutCalls = 0

        override suspend fun currentProfile(): Profile? {
            failure?.let { throw it }
            return profile
        }

        override suspend fun signOut() {
            failure?.let { throw it }
            signOutCalls++
            profile = null
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun signedInStateCarriesDisplayName() = runTest {
        val viewModel = HomeViewModel(FakeRepository(profile = Fixtures.decode("profile.json")))
        assertEquals(HomeUiState.SignedIn("Jonathan"), viewModel.uiState.value)
    }

    @Test fun noProfileMeansSignedOut() = runTest {
        val viewModel = HomeViewModel(FakeRepository())
        assertEquals(HomeUiState.SignedOut, viewModel.uiState.value)
    }

    @Test fun repositoryFailureBecomesErrorState() = runTest {
        val viewModel = HomeViewModel(FakeRepository(failure = IllegalStateException("réseau")))
        assertEquals(HomeUiState.Error, viewModel.uiState.value)
    }

    @Test fun refreshRecoversFromError() = runTest {
        val repository = FakeRepository(failure = IllegalStateException("réseau"))
        val viewModel = HomeViewModel(repository)
        assertTrue(viewModel.uiState.value is HomeUiState.Error)
        repository.failure = null
        repository.profile = Fixtures.decode("profile.json")
        viewModel.refresh()
        assertEquals(HomeUiState.SignedIn("Jonathan"), viewModel.uiState.value)
    }

    @Test fun signOutClearsSession() = runTest {
        val repository = FakeRepository(profile = Fixtures.decode("profile.json"))
        val viewModel = HomeViewModel(repository)
        viewModel.signOut()
        assertEquals(1, repository.signOutCalls)
        assertEquals(HomeUiState.SignedOut, viewModel.uiState.value)
    }
}
