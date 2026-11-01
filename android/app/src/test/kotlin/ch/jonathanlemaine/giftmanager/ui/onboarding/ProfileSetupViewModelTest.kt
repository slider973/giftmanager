package ch.jonathanlemaine.giftmanager.ui.onboarding

import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.SessionPhase
import ch.jonathanlemaine.giftmanager.core.session.profile
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
class ProfileSetupViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.fixture(): SessionFixture {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.profiles.me = profile(name = "", onboarded = false)
        f.session.start()
        runCurrent()
        return f
    }

    private val fr = Locale.Builder().setLanguage("fr").setRegion("FR").build()

    @Test fun startsFromDeviceRegionAndItsCurrency() = runTest {
        val vm = ProfileSetupViewModel(fixture().session, fr)
        assertEquals("FR", vm.uiState.value.country)
        assertEquals("EUR", vm.uiState.value.currency)
        assertEquals("", vm.uiState.value.name)
        assertFalse(vm.uiState.value.canSave)
    }

    @Test fun unsupportedRegionFallsBackToSwitzerland() = runTest {
        val vm = ProfileSetupViewModel(fixture().session, Locale.Builder().setLanguage("ja").setRegion("JP").build())
        assertEquals("CH", vm.uiState.value.country)
        assertEquals("CHF", vm.uiState.value.currency)
    }

    @Test fun changingCountrySuggestsCurrencyButCurrencyStaysEditable() = runTest {
        val vm = ProfileSetupViewModel(fixture().session, fr)
        vm.onCountryChange("GB")
        assertEquals("GBP", vm.uiState.value.currency)
        vm.onCurrencyChange("USD")
        assertEquals("USD", vm.uiState.value.currency)
        vm.onCountryChange("ZZ")
        assertEquals("USD", vm.uiState.value.currency)
    }

    @Test fun blankNameCannotBeSaved() = runTest {
        val f = fixture()
        val vm = ProfileSetupViewModel(f.session, fr)
        vm.onNameChange("   ")
        assertFalse(vm.uiState.value.canSave)
        vm.save()
        assertTrue(f.profiles.updates.isEmpty())
    }

    @Test fun saveTrimsNameAndAdvancesThePhase() = runTest {
        val f = fixture()
        val vm = ProfileSetupViewModel(f.session, fr)
        vm.onNameChange("  Marie ")
        vm.save()
        runCurrent()
        assertEquals("Marie", f.profiles.updates.single().displayName)
        assertEquals("FR", f.profiles.updates.single().country)
        assertEquals(SessionPhase.NeedsGroup, f.session.state.value.phase)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test fun failureIsExposedAndDismissible() = runTest {
        val f = fixture()
        val vm = ProfileSetupViewModel(f.session, fr)
        vm.onNameChange("Marie")
        f.profiles.failure = IOException("réseau")
        vm.save()
        runCurrent()
        assertEquals(GiftError.Network, vm.uiState.value.error)
        assertEquals(SessionPhase.NeedsProfile, f.session.state.value.phase)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }
}
