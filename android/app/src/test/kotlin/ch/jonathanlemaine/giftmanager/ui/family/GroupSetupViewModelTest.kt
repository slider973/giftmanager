package ch.jonathanlemaine.giftmanager.ui.family

import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.SessionPhase
import ch.jonathanlemaine.giftmanager.core.session.group
import ch.jonathanlemaine.giftmanager.core.session.household
import ch.jonathanlemaine.giftmanager.core.session.profile
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
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
class GroupSetupViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** Utilisateur sans famille. */
    private suspend fun TestScope.fixture(setup: SessionFixture.() -> Unit = {}): SessionFixture {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.profiles.me = profile(name = "Jo")
        f.setup()
        f.session.start()
        runCurrent()
        return f
    }

    @Test fun startsOnChooseWithSuggestedHouseholdName() = runTest {
        val vm = GroupSetupViewModel(fixture().session)
        assertEquals(GroupSetupMode.Choose, vm.uiState.value.mode)
        assertEquals("Foyer de Jo", vm.uiState.value.householdName)
    }

    @Test fun navigationBetweenSubStates() = runTest {
        val vm = GroupSetupViewModel(fixture().session)
        vm.showCreate()
        assertEquals(GroupSetupMode.Create, vm.uiState.value.mode)
        vm.showChoose()
        vm.showJoin()
        assertEquals(GroupSetupMode.Join, vm.uiState.value.mode)
        vm.showChoose()
        assertEquals(GroupSetupMode.Choose, vm.uiState.value.mode)
    }

    @Test fun createRequiresBothNames() = runTest {
        val vm = GroupSetupViewModel(fixture().session)
        vm.onGroupNameChange("  ")
        assertFalse(vm.uiState.value.canCreateGroup)
        vm.onGroupNameChange("Les Lemaine")
        assertTrue(vm.uiState.value.canCreateGroup)
        vm.onHouseholdNameChange("")
        assertFalse(vm.uiState.value.canCreateGroup)
    }

    @Test fun codeLengthsAreSixForFamilyAndEightForHousehold() = runTest {
        val vm = GroupSetupViewModel(fixture().session)
        vm.onGroupCodeChange("abc12")
        assertFalse(vm.uiState.value.canJoinGroup)
        vm.onGroupCodeChange("abc123")
        assertTrue(vm.uiState.value.canJoinGroup)
        assertEquals("ABC123", vm.uiState.value.groupCode)
        vm.onHouseholdCodeChange("abcdefg")
        assertFalse(vm.uiState.value.canJoinHousehold)
        vm.onHouseholdCodeChange("abcdefgh")
        assertTrue(vm.uiState.value.canJoinHousehold)
    }

    @Test fun createGroupTrimsNamesAndMovesToReady() = runTest {
        val f = fixture()
        // Le serveur crée aussi le foyer du créateur : on le simule dans le fake.
        val vm = GroupSetupViewModel(f.session)
        vm.onGroupNameChange("  Les Lemaine ")
        vm.createGroup()
        runCurrent()
        assertEquals("Les Lemaine" to "Foyer de Jo", f.families.created!!.let { it.first to it.second })
        assertEquals("Les Lemaine", f.session.state.value.currentGroup?.name)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test fun pendingInviteCodeOpensJoinPrefilled() = runTest {
        val f = fixture()
        f.session.handleDeepLink("famillecadeaux://join/abc123")
        val vm = GroupSetupViewModel(f.session)
        assertEquals(GroupSetupMode.Join, vm.uiState.value.mode)
        assertEquals("ABC123", vm.uiState.value.groupCode)
    }

    @Test fun inviteArrivingWhileOnScreenIsPickedUp() = runTest {
        val f = fixture()
        val vm = GroupSetupViewModel(f.session)
        f.session.handleDeepLink("famillecadeaux://join?code=zzz999")
        assertEquals(GroupSetupMode.Join, vm.uiState.value.mode)
        assertEquals("ZZZ999", vm.uiState.value.groupCode)
    }

    @Test fun joinGroupMovesToHouseholdStepAndClearsPendingCode() = runTest {
        val f = fixture { families.joinable["ABC123"] = group(7) }
        f.session.handleDeepLink("famillecadeaux://join/ABC123")
        val vm = GroupSetupViewModel(f.session)
        vm.joinGroup()
        runCurrent()
        assertEquals(GroupSetupMode.Household, vm.uiState.value.mode)
        assertEquals("Famille 7", vm.uiState.value.currentGroupName)
        assertNull(f.session.pendingInviteCode.value)
        assertNull(vm.uiState.value.error)
    }

    @Test fun invalidCodeShowsErrorAndStaysOnJoin() = runTest {
        val f = fixture()
        val vm = GroupSetupViewModel(f.session)
        vm.showJoin()
        vm.onGroupCodeChange("NOPE00")
        vm.joinGroup()
        runCurrent()
        assertEquals(GiftError.InvalidInvite, vm.uiState.value.error)
        assertEquals(GroupSetupMode.Join, vm.uiState.value.mode)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test fun householdStepCreatesHouseholdAndReachesReady() = runTest {
        val f = fixture { families.joinable["ABC123"] = group(7) }
        val vm = GroupSetupViewModel(f.session)
        vm.onGroupCodeChange("ABC123")
        vm.joinGroup()
        runCurrent()
        vm.onHouseholdNameChange("Foyer Jo")
        vm.createHousehold()
        runCurrent()
        assertEquals(listOf("create:Foyer Jo"), f.households.calls)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
    }

    @Test fun householdStepJoinsSpouseHousehold() = runTest {
        val f = fixture {
            families.joinable["ABC123"] = group(7)
            households.all += household(5)
            households.joinable["HH000005"] = household(5)
        }
        // Le foyer du conjoint est partagé avec la famille rejointe.
        f.households.shares += ch.jonathanlemaine.giftmanager.core.model.HouseholdGroup(household(5).id, group(7).id)
        val vm = GroupSetupViewModel(f.session)
        vm.onGroupCodeChange("ABC123")
        vm.joinGroup()
        runCurrent()
        vm.onHouseholdCodeChange("hh000005")
        vm.joinHousehold()
        runCurrent()
        assertEquals(listOf("join:HH000005"), f.households.calls)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
    }

    @Test fun signOutLeavesTheSession() = runTest {
        val f = fixture()
        GroupSetupViewModel(f.session).signOut()
        runCurrent()
        assertEquals(SessionPhase.SignedOut, f.session.state.value.phase)
    }
}
