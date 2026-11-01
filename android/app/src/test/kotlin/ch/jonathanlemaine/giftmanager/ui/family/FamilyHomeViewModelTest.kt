package ch.jonathanlemaine.giftmanager.ui.family

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.SessionPhase
import ch.jonathanlemaine.giftmanager.core.session.group
import ch.jonathanlemaine.giftmanager.core.session.household
import ch.jonathanlemaine.giftmanager.core.session.profile
import ch.jonathanlemaine.giftmanager.core.session.testChild
import ch.jonathanlemaine.giftmanager.core.session.testEvent
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import java.io.IOException
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
class FamilyHomeViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.fixture(setup: SessionFixture.() -> Unit = { seedReady(1, 2) }): SessionFixture {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.setup()
        f.session.start()
        runCurrent()
        return f
    }

    @Test fun summaryAndInviteMessage() = runTest {
        val vm = FamilyHomeViewModel(fixture().session)
        val s = vm.uiState.value
        assertEquals("Famille 1", s.group?.name)
        assertEquals(1, s.memberCount)
        assertEquals(1, s.householdCount)
        assertEquals(listOf("Jonathan"), s.memberNames)
        assertTrue(s.inviteMessage!!.contains("Code d'invitation : CODE01"))
        assertEquals(0, s.tab)
    }

    @Test fun tabIsClamped() = runTest {
        val vm = FamilyHomeViewModel(fixture().session)
        vm.selectTab(2)
        assertEquals(2, vm.uiState.value.tab)
        vm.selectTab(9)
        assertEquals(2, vm.uiState.value.tab)
        vm.selectTab(-1)
        assertEquals(0, vm.uiState.value.tab)
    }

    @Test fun myHouseholdComesFirstWithParents() = runTest {
        val f = fixture {
            seedReady(1)
            households.all += household(2)
            households.shares += ch.jonathanlemaine.giftmanager.core.model.HouseholdGroup(household(2).id, group(1).id)
            households.memberships += HouseholdMember(household(2).id, uuid(2))
            families.members[group(1).id]!!.add(uuid(2))
            profiles.others += profile(uuid(2), "Marie")
        }
        val items = FamilyHomeViewModel(f.session).uiState.value.households
        assertEquals(listOf(true, false), items.map { it.isMine })
        assertEquals(listOf(listOf("Jonathan"), listOf("Marie")), items.map { it.parentNames })
    }

    @Test fun renameStateOnlyAllowsARealChange() {
        val g = group(1, name = "Les Lemaine")
        val state = RenameGroupState(g)
        assertFalse(state.canRename)
        assertFalse(state.copy(name = "   ").canRename)
        assertFalse(state.copy(name = "  Les Lemaine ").canRename)
        assertTrue(state.copy(name = "Les Dupont").canRename)
    }

    @Test fun renameFlow() = runTest {
        val f = fixture()
        val vm = FamilyHomeViewModel(f.session)
        assertFalse(vm.uiState.value.rename!!.canRename)
        vm.onRenameChange("  Les Dupont ")
        assertTrue(vm.uiState.value.rename!!.canRename)
        vm.rename()
        runCurrent()
        assertEquals(group(1).id to "Les Dupont", f.families.renamed)
        assertEquals("Les Dupont", vm.uiState.value.group?.name)
        assertFalse(vm.uiState.value.rename!!.canRename)
    }

    @Test fun renameFieldNeverCarriesTheOldNameToAnotherGroup() = runTest {
        val vm = FamilyHomeViewModel(fixture().session)
        vm.onRenameChange("Brouillon")
        vm.selectGroup(group(2))
        runCurrent()
        val rename = vm.uiState.value.rename!!
        assertEquals("Famille 2", rename.name)
        assertFalse(rename.canRename)
    }

    @Test fun switchingGroupPersistsAndUpdatesState() = runTest {
        val f = fixture()
        val vm = FamilyHomeViewModel(f.session)
        assertEquals(2, vm.uiState.value.groups.size)
        vm.selectGroup(group(2))
        runCurrent()
        assertEquals("Famille 2", vm.uiState.value.group?.name)
        assertEquals(group(2).id, f.store.saved)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test fun selectingTheCurrentGroupDoesNothing() = runTest {
        val f = fixture()
        val before = f.profiles.myProfileCalls
        FamilyHomeViewModel(f.session).selectGroup(group(1))
        runCurrent()
        assertEquals(before, f.profiles.myProfileCalls)
    }

    @Test fun joinOtherGroupWithCode() = runTest {
        val f = fixture { seedReady(1); families.joinable["NEW123"] = group(3) }
        val vm = FamilyHomeViewModel(f.session)
        vm.showJoinDialog()
        vm.onJoinCodeChange("new12")
        assertFalse(vm.uiState.value.canJoin)
        vm.onJoinCodeChange("new123")
        assertTrue(vm.uiState.value.canJoin)
        vm.joinOtherGroup()
        runCurrent()
        assertFalse(vm.uiState.value.showJoinDialog)
        assertEquals("Famille 3", vm.uiState.value.group?.name)
        // join_group partage automatiquement mon foyer : on reste Ready.
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
        assertNull(vm.uiState.value.error)
    }

    @Test fun joinWithBadCodeShowsError() = runTest {
        val vm = FamilyHomeViewModel(fixture().session)
        vm.onJoinCodeChange("NOPE00")
        vm.joinOtherGroup()
        runCurrent()
        assertEquals(GiftError.InvalidInvite, vm.uiState.value.error)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test fun refreshStopsSpinnerAndReportsFailure() = runTest {
        val f = fixture()
        val vm = FamilyHomeViewModel(f.session)
        f.households.failure = IOException("réseau")
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(GiftError.Network, vm.uiState.value.error)
    }

    // region Enfants et événements (#72)

    @Test fun householdsCarryTheirChildrenThenAdults() = runTest {
        val f = fixture {
            seedReady(1)
            childRepo.all += listOf(testChild(1, name = "Moi", adult = true), testChild(2, name = "Léa"), testChild(3, householdN = 2))
        }
        val items = FamilyHomeViewModel(f.session, Fixtures.clock).uiState.value.households
        assertEquals(listOf("Léa", "Moi"), items.single { it.isMine }.children.map { it.firstName })
    }

    @Test fun eventsAreSplitIntoUpcomingAndPast() = runTest {
        val f = fixture {
            seedReady(1)
            eventRepo.all += listOf(testEvent(1, "2026-12-25", kind = GiftEventKind.CHRISTMAS), testEvent(2, "2026-10-01"), testEvent(3, "2026-11-01"))
        }
        val s = FamilyHomeViewModel(f.session, Fixtures.clock).uiState.value
        assertEquals(listOf("Événement 3", "Événement 1"), s.upcomingEvents.map { it.event.title })
        assertEquals(listOf("Événement 2"), s.pastEvents.map { it.event.title })
        assertEquals(uuid(501), s.currentEventId)
    }

    @Test fun eventRowCarriesCountdownAgeAndEditRight() = runTest {
        val zoe = testChild(1, householdN = 2, name = "Zoé", birthdate = "2018-11-10")
        val f = fixture {
            seedReady(1)
            childRepo.all += zoe
            eventRepo.all += listOf(
                testEvent(1, "2026-11-10", kind = GiftEventKind.BIRTHDAY, childId = zoe.id),
                testEvent(2, "2026-11-20"),
            )
        }
        val s = FamilyHomeViewModel(f.session, Fixtures.clock).uiState.value
        val birthday = s.upcomingEvents.first { it.event.kind == GiftEventKind.BIRTHDAY }
        assertEquals(8, birthday.age)
        assertEquals(26, birthday.daysRemaining)
        assertEquals(listOf("Zoé"), birthday.avatarNames)
        assertFalse(birthday.canEdit)
        val other = s.upcomingEvents.first { it.event.kind == GiftEventKind.OTHER }
        assertTrue(other.canEdit)
        assertNull(other.age)
    }

    // endregion
}
