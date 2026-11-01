package ch.jonathanlemaine.giftmanager.ui.profile

import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.FakeLocalPrefs
import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.SessionPhase
import ch.jonathanlemaine.giftmanager.core.session.group
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import java.io.IOException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private class Setup(val f: SessionFixture, val prefs: FakeLocalPrefs, val vm: ProfileViewModel)

    private suspend fun TestScope.setup(groups: IntArray = intArrayOf(1, 2)): Setup {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady(*groups)
        f.session.start()
        runCurrent()
        val prefs = FakeLocalPrefs()
        return Setup(f, prefs, ProfileViewModel(f.session, prefs))
    }

    @Test fun initialStateFromProfile() = runTest {
        val s = setup().vm.uiState.value
        assertEquals("Jonathan", s.name)
        assertEquals("Jonathan", s.displayName)
        assertEquals("CH", s.country)
        assertEquals("Foyer 1", s.householdName)
        assertFalse(s.hasChanges)
        assertTrue(s.purchaseReminders && s.birthdayReminders && s.familyNews)
        assertEquals(listOf(group(1), group(2)), s.sharedFamilies)
        assertTrue(s.canUnshare)
    }

    @Test fun hasChangesTracksEditsAgainstProfile() = runTest {
        val vm = setup().vm
        vm.onNameChange("Jo")
        assertTrue(vm.uiState.value.hasChanges)
        vm.onNameChange("  Jonathan ")
        assertFalse(vm.uiState.value.hasChanges)
        vm.onCurrencyChange("EUR")
        assertTrue(vm.uiState.value.hasChanges)
        vm.onCurrencyChange("CHF")
        assertFalse(vm.uiState.value.hasChanges)
    }

    @Test fun countryChangeSuggestsCurrency() = runTest {
        val vm = setup().vm
        vm.onCountryChange("FR")
        assertEquals("EUR", vm.uiState.value.currency)
        assertTrue(vm.uiState.value.hasChanges)
    }

    @Test fun blankNameCannotBeSaved() = runTest {
        val vm = setup().vm
        vm.onNameChange("  ")
        assertTrue(vm.uiState.value.hasChanges)
        assertFalse(vm.uiState.value.canSave)
    }

    @Test fun saveStoresTrimmedProfileAndClearsChanges() = runTest {
        val s = setup()
        s.vm.onNameChange(" Marie ")
        s.vm.onCountryChange("FR")
        s.vm.save()
        runCurrent()
        assertEquals("Marie", s.f.profiles.updates.single().displayName)
        assertEquals("EUR", s.f.profiles.updates.single().currency)
        assertEquals("Marie", s.vm.uiState.value.displayName)
        assertFalse(s.vm.uiState.value.hasChanges)
        assertFalse(s.vm.uiState.value.isSaving)
        assertEquals(SessionPhase.Ready, s.f.session.state.value.phase)
    }

    @Test fun saveFailureKeepsEditsAndReportsError() = runTest {
        val s = setup()
        s.vm.onNameChange("Marie")
        s.f.profiles.failure = IOException("réseau")
        s.vm.save()
        runCurrent()
        assertEquals(GiftError.Network, s.vm.uiState.value.error)
        assertTrue(s.vm.uiState.value.hasChanges)
        assertEquals("Marie", s.vm.uiState.value.name)
    }

    @Test fun togglesAreOptimisticThenPersisted() = runTest {
        val s = setup()
        s.vm.setBirthdayReminders(false)
        runCurrent()
        assertFalse(s.vm.uiState.value.birthdayReminders)
        assertFalse(s.f.session.state.value.profile!!.notifyBirthdayReminders)
        s.vm.setPurchaseReminders(false)
        runCurrent()
        assertFalse(s.vm.uiState.value.purchaseReminders)
        assertTrue(s.vm.uiState.value.birthdayReminders.not())
    }

    @Test fun toggleRollsBackWhenTheCallFails() = runTest {
        val s = setup()
        s.f.profiles.failure = IOException("réseau")
        s.vm.setPurchaseReminders(false)
        runCurrent()
        assertTrue(s.vm.uiState.value.purchaseReminders)
        assertEquals(GiftError.Network, s.vm.uiState.value.error)
        s.vm.setBirthdayReminders(false)
        runCurrent()
        assertTrue(s.vm.uiState.value.birthdayReminders)
    }

    @Test fun toggleShowsNewValueWhileTheCallIsInFlight() = runTest {
        val s = setup()
        val gate = CompletableDeferred<Unit>()
        val slow = object : ch.jonathanlemaine.giftmanager.data.ProfileRepository by s.f.profiles {
            override suspend fun setBirthdayReminders(userId: java.util.UUID, enabled: Boolean) {
                gate.await()
                s.f.profiles.setBirthdayReminders(userId, enabled)
            }
        }
        val session = ch.jonathanlemaine.giftmanager.core.session.AppSession(
            s.f.authRepo, slow, s.f.families, s.f.households, s.f.childRepo, s.f.eventRepo, s.f.store, backgroundScope,
        )
        session.start()
        runCurrent()
        val vm = ProfileViewModel(session, s.prefs)
        vm.setBirthdayReminders(false)
        runCurrent()
        assertFalse(vm.uiState.value.birthdayReminders)
        gate.complete(Unit)
        runCurrent()
        assertFalse(vm.uiState.value.birthdayReminders)
        assertNull(vm.uiState.value.error)
    }

    @Test fun familyNewsIsALocalPreference() = runTest {
        val s = setup()
        s.vm.setFamilyNews(false)
        runCurrent()
        assertFalse(s.vm.uiState.value.familyNews)
        assertTrue(s.f.profiles.updates.isEmpty())
    }

    @Test fun unshareNeedsConfirmationAndIsOnlyOfferedWithSeveralFamilies() = runTest {
        val s = setup()
        s.vm.requestUnshare(group(2))
        assertEquals(group(2), s.vm.uiState.value.unsharing)
        s.vm.cancelUnshare()
        assertNull(s.vm.uiState.value.unsharing)
        s.vm.requestUnshare(group(2))
        s.vm.confirmUnshare()
        runCurrent()
        assertEquals(listOf("unshare:${group(2).id}"), s.f.households.calls)
        assertNull(s.vm.uiState.value.unsharing)
        assertEquals(listOf(group(1)), s.vm.uiState.value.sharedFamilies)
        assertFalse(s.vm.uiState.value.canUnshare)
    }

    @Test fun deleteAccountNeedsConfirmation() = runTest {
        val s = setup()
        s.vm.requestDelete()
        assertTrue(s.vm.uiState.value.confirmingDelete)
        s.vm.cancelDelete()
        assertFalse(s.vm.uiState.value.confirmingDelete)
        assertFalse(s.f.profiles.deleted)
        s.vm.requestDelete()
        s.vm.confirmDelete()
        runCurrent()
        assertTrue(s.f.profiles.deleted)
        assertEquals(SessionPhase.SignedOut, s.f.session.state.value.phase)
    }

    @Test fun deleteFailureKeepsSessionAndShowsError() = runTest {
        val s = setup()
        s.f.profiles.failure = IOException("réseau")
        s.vm.confirmDelete()
        runCurrent()
        assertEquals(GiftError.Network, s.vm.uiState.value.error)
        assertEquals(SessionPhase.Ready, s.f.session.state.value.phase)
    }

    @Test fun signOut() = runTest {
        val s = setup()
        s.vm.signOut()
        runCurrent()
        assertEquals(SessionPhase.SignedOut, s.f.session.state.value.phase)
    }
}
