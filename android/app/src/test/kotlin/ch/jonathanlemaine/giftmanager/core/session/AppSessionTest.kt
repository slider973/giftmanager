package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.model.HouseholdGroup
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppSessionTest {
    private val me = uuid(1)

    private suspend fun TestScope.started(
        auth: AuthState = AuthState.SignedIn(me),
        setup: SessionFixture.() -> Unit = { seedReady() },
    ): SessionFixture {
        val f = SessionFixture(backgroundScope, auth)
        f.setup()
        f.session.start()
        runCurrent()
        return f
    }

    @Test fun launchingWhileAuthIsLoading() = runTest {
        val f = started(AuthState.Loading)
        assertEquals(SessionPhase.Launching, f.session.state.value.phase)
    }

    @Test fun signedOut() = runTest {
        val f = started(AuthState.SignedOut)
        assertEquals(SessionPhase.SignedOut, f.session.state.value.phase)
        assertNull(f.session.state.value.userId)
    }

    @Test fun readyWhenProfileGroupAndHouseholdExist() = runTest {
        val f = started()
        val s = f.session.state.value
        assertEquals(SessionPhase.Ready, s.phase)
        assertEquals(me, s.userId)
        assertEquals(group(1), s.currentGroup)
        assertEquals(household(1), s.myHousehold)
        assertEquals(listOf("Jonathan"), s.members.map { it.displayName })
        assertEquals(1, f.birthdayCalls)
    }

    @Test fun needsProfileWhenNotOnboarded() = runTest {
        val f = started { seedReady(); profiles.me = profile(onboarded = false) }
        assertEquals(SessionPhase.NeedsProfile, f.session.state.value.phase)
        assertTrue(f.session.state.value.groups.isEmpty())
    }

    @Test fun needsGroupWithoutAnyGroup() = runTest {
        val f = started { }
        assertEquals(SessionPhase.NeedsGroup, f.session.state.value.phase)
        assertNull(f.session.state.value.currentGroup)
    }

    @Test fun needsGroupWhenGroupHasNoHouseholdOfMine() = runTest {
        val f = started { families.groups += group(1); families.members[group(1).id] = mutableListOf(me) }
        val s = f.session.state.value
        assertEquals(SessionPhase.NeedsGroup, s.phase)
        assertEquals(group(1), s.currentGroup)
        assertNull(s.myHousehold)
    }

    @Test fun savedGroupIsRestoredElseFirst() = runTest {
        val f = started { seedReady(1, 2); store.saved = group(2).id }
        assertEquals(group(2), f.session.state.value.currentGroup)
        val g = started { seedReady(1, 2); store.saved = uuid(999) }
        assertEquals(group(1), g.session.state.value.currentGroup)
    }

    @Test fun selectGroupPersistsAndReloads() = runTest {
        val f = started { seedReady(1, 2) }
        assertTrue(f.session.selectGroup(group(2)).isSuccess)
        assertEquals(group(2).id, f.store.saved)
        assertEquals(group(2), f.session.state.value.currentGroup)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
    }

    @Test fun firstLoadFailureDoesNotStayLaunching() = runTest {
        val f = started { seedReady(); profiles.failure = IOException("réseau") }
        val s = f.session.state.value
        assertEquals(SessionPhase.SignedOut, s.phase)
        assertTrue(s.loadFailed)
        assertEquals(me, s.userId)
        // Réessayer une fois le réseau revenu.
        f.profiles.failure = null
        assertTrue(f.session.refreshAll().isSuccess)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
        assertFalse(f.session.state.value.loadFailed)
    }

    @Test fun laterLoadFailureKeepsPhase() = runTest {
        val f = started()
        f.profiles.failure = IOException("réseau")
        assertTrue(f.session.refreshAll().isFailure)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
    }

    @Test fun sameUserDoesNotReload() = runTest {
        val f = started()
        val calls = f.profiles.myProfileCalls
        f.authRepo.stateFlow.value = AuthState.Loading
        runCurrent()
        f.authRepo.stateFlow.value = AuthState.SignedIn(me)
        runCurrent()
        assertEquals(calls, f.profiles.myProfileCalls)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
    }

    @Test fun otherUserReloads() = runTest {
        val f = started()
        f.profiles.me = profile(id = uuid(2), name = "Marie", onboarded = false)
        f.authRepo.stateFlow.value = AuthState.SignedIn(uuid(2))
        runCurrent()
        assertEquals(SessionPhase.NeedsProfile, f.session.state.value.phase)
        assertEquals(uuid(2), f.session.state.value.userId)
    }

    @Test fun signOutResetsEverything() = runTest {
        val f = started()
        f.session.signOut()
        runCurrent()
        assertEquals(SessionState(phase = SessionPhase.SignedOut), f.session.state.value)
        assertEquals(1, f.authRepo.signOutCalls)
    }

    @Test fun authSignedOutEventResets() = runTest {
        val f = started()
        f.authRepo.stateFlow.value = AuthState.SignedOut
        runCurrent()
        assertEquals(SessionPhase.SignedOut, f.session.state.value.phase)
        assertNull(f.session.state.value.profile)
    }

    @Test fun signOutFailureStillResets() = runTest {
        val f = started()
        f.authRepo.signOutFailure = IOException("réseau")
        f.session.signOut()
        assertEquals(SessionPhase.SignedOut, f.session.state.value.phase)
    }

    @Test fun saveProfileMovesOnToGroupStep() = runTest {
        val f = started { profiles.me = profile(onboarded = false) }
        assertTrue(f.session.saveProfile("Jo", "FR", "EUR").isSuccess)
        val s = f.session.state.value
        assertEquals(SessionPhase.NeedsGroup, s.phase)
        assertEquals("Jo", s.profile?.displayName)
        assertEquals("EUR", s.profile?.currency)
        assertTrue(s.profile!!.onboarded)
    }

    @Test fun saveProfileKeepsRemindersAndPhaseWhenReady() = runTest {
        val f = started()
        assertTrue(f.session.setBirthdayReminders(false).isSuccess)
        f.session.saveProfile("Jo", "CH", "CHF")
        assertFalse(f.session.state.value.profile!!.notifyBirthdayReminders)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
    }

    @Test fun createGroupUsesProfileCountryAndBecomesCurrent() = runTest {
        val f = started { profiles.me = profile(country = "FR") }
        assertEquals(SessionPhase.NeedsGroup, f.session.state.value.phase)
        // Le fake ne crée pas de foyer côté serveur : on le simule pour atteindre Ready.
        f.session.createGroup("Les Lemaine", "Foyer Jo")
        assertEquals(Triple("Les Lemaine", "Foyer Jo", "FR"), f.families.created)
        assertEquals(f.families.groups.single().id, f.store.saved)
        assertEquals("Les Lemaine", f.session.state.value.currentGroup?.name)
    }

    @Test fun createGroupFailureIsReturned() = runTest {
        val f = started { }
        f.families.failure = IOException("réseau")
        assertTrue(f.session.createGroup("A", "B").isFailure)
    }

    @Test fun joinGroupThenCreateHousehold() = runTest {
        val f = started { }
        f.families.joinable["ABC123"] = group(7)
        val id = f.session.joinGroup("ABC123").getOrThrow()
        assertEquals(group(7).id, id)
        assertEquals(id, f.store.saved)
        assertEquals(group(7), f.session.state.value.currentGroup)
        assertEquals(SessionPhase.NeedsGroup, f.session.state.value.phase)
        assertTrue(f.session.createHousehold("Foyer de Jo").isSuccess)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
        assertEquals("Foyer de Jo", f.session.state.value.myHousehold?.name)
    }

    @Test fun joinGroupWithInvalidCodeFails() = runTest {
        val f = started { }
        assertTrue(f.session.joinGroup("NOPE00").isFailure)
        assertNull(f.store.saved)
    }

    @Test fun joinHouseholdOfSpouse() = runTest {
        val f = started {
            families.groups += group(1)
            families.members[group(1).id] = mutableListOf(me)
            households.all += household(5)
            households.shares += HouseholdGroup(household(5).id, group(1).id)
            households.joinable["HH000005"] = household(5)
        }
        assertEquals(SessionPhase.NeedsGroup, f.session.state.value.phase)
        assertTrue(f.session.joinHousehold("HH000005").isSuccess)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
    }

    @Test fun joiningAnotherGroupSharesMyHouseholdAutomatically() = runTest {
        val f = started { seedReady(1) }
        f.families.joinable["ABC123"] = group(2)
        f.session.joinGroup("ABC123").getOrThrow()
        f.session.refreshAll()
        val s = f.session.state.value
        assertEquals(SessionPhase.Ready, s.phase)
        assertEquals(listOf(group(1), group(2)), s.familiesSharingMyHousehold)
    }

    @Test fun shareAndUnshareHousehold() = runTest {
        val f = started { seedReady(1); families.groups += group(2); families.members[group(2).id] = mutableListOf(me) }
        f.session.selectGroup(group(2))
        // Famille 2 sans mon foyer (retiré, ou famille créée ailleurs) : il faut le partager ou en créer un.
        assertEquals(SessionPhase.NeedsGroup, f.session.state.value.phase)
        assertTrue(f.session.shareHousehold().isSuccess)
        val s = f.session.state.value
        assertEquals(SessionPhase.Ready, s.phase)
        assertEquals(listOf(group(1), group(2)), s.familiesSharingMyHousehold)
        assertFalse(s.canShareHouseholdWithCurrentGroup)
        assertTrue(f.session.unshareHousehold(group(1)).isSuccess)
        assertEquals(listOf(group(2)), f.session.state.value.familiesSharingMyHousehold)
    }

    @Test fun parentsOfHousehold() = runTest {
        val f = started { seedReady(); families.members[group(1).id]!!.add(uuid(2)); profiles.others += profile(uuid(2), "Marie"); households.memberships += HouseholdMember(household(1).id, uuid(2)) }
        val s = f.session.state.value
        assertEquals(listOf("Jonathan", "Marie"), s.parents(household(1)).map { it.displayName })
    }

    @Test fun renameGroupReloads() = runTest {
        val f = started()
        assertTrue(f.session.renameGroup(group(1).id, "Les Dupont").isSuccess)
        assertEquals("Les Dupont", f.session.state.value.currentGroup?.name)
    }

    @Test fun remindersUpdateProfileAndSkipNoops() = runTest {
        val f = started()
        assertTrue(f.session.setPurchaseReminders(false).isSuccess)
        assertFalse(f.session.state.value.profile!!.notifyPurchaseReminders)
        f.profiles.failure = IOException("réseau")
        // Même valeur : aucun appel serveur, donc pas d'échec.
        assertTrue(f.session.setPurchaseReminders(false).isSuccess)
        assertTrue(f.session.setBirthdayReminders(false).isFailure)
        assertTrue(f.session.state.value.profile!!.notifyBirthdayReminders)
    }

    @Test fun deleteAccountSignsOut() = runTest {
        val f = started()
        assertTrue(f.session.deleteAccount().isSuccess)
        assertTrue(f.profiles.deleted)
        assertEquals(SessionPhase.SignedOut, f.session.state.value.phase)
    }

    @Test fun deleteAccountFailureKeepsSession() = runTest {
        val f = started()
        f.profiles.failure = IOException("réseau")
        assertTrue(f.session.deleteAccount().isFailure)
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
    }

    @Test fun householdGroupsFailureIsTolerated() = runTest {
        val f = started { seedReady(); households.groupsFailure = IOException("x") }
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
        assertTrue(f.session.state.value.householdGroups.isEmpty())
    }

    @Test fun birthdayHookFailureDoesNotBlock() = runTest {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(me))
        f.seedReady()
        f.eventRepo.ensureFailure = IOException("boom")
        val s = f.session
        s.start()
        runCurrent()
        assertEquals(SessionPhase.Ready, s.state.value.phase)
    }

    @Test fun refreshGroupDataReloadsOnlyGroup() = runTest {
        val f = started()
        val profileCalls = f.profiles.myProfileCalls
        assertTrue(f.session.refreshGroupData().isSuccess)
        assertEquals(profileCalls, f.profiles.myProfileCalls)
    }

    @Test fun deepLinkSetsPendingCode() = runTest {
        val f = started()
        f.session.handleDeepLink("famillecadeaux://join/abc123")
        assertEquals("ABC123", f.session.pendingInviteCode.value)
        f.session.handleDeepLink("https://example.com/join/zzz")
        assertEquals("ABC123", f.session.pendingInviteCode.value)
        f.session.clearPendingInviteCode()
        assertNotNull(f.session.state.value.userId)
        assertNull(f.session.pendingInviteCode.value)
    }

    // region Enfants et événements (#72)

    @Test fun loadsChildrenAndEventsOfTheGroup() = runTest {
        val f = started {
            seedReady()
            childRepo.all += listOf(testChild(1), testChild(2, householdN = 2))
            eventRepo.all += testEvent(1, "2026-12-25")
        }
        val s = f.session.state.value
        assertEquals(listOf(testChild(1), testChild(2, householdN = 2)), s.children)
        assertEquals(listOf(testEvent(1, "2026-12-25")), s.events)
    }

    @Test fun duplicateChildrenFromSeveralSharesAreMerged() = runTest {
        val f = started { seedReady(); childRepo.all += listOf(testChild(1), testChild(1), testChild(2)) }
        assertEquals(listOf(uuid(401), uuid(402)), f.session.state.value.children.map { it.id })
    }

    @Test fun birthdayEventsAreEnsuredBeforeEachGroupLoad() = runTest {
        val f = started()
        assertEquals(1, f.eventRepo.ensureCalls)
        f.session.refreshGroupData()
        assertEquals(2, f.eventRepo.ensureCalls)
        f.session.reloadGroup()
        assertEquals(3, f.eventRepo.ensureCalls)
    }

    @Test fun ensureBirthdayEventsFailureIsIgnored() = runTest {
        val f = started { seedReady(); eventRepo.ensureFailure = IOException("boom"); childRepo.all += testChild(1) }
        assertEquals(SessionPhase.Ready, f.session.state.value.phase)
        assertEquals(1, f.session.state.value.children.size)
    }

    @Test fun reloadGroupPicksUpChangesWithoutReloadingTheProfile() = runTest {
        val f = started()
        val profileCalls = f.profiles.myProfileCalls
        f.childRepo.all += testChild(3)
        f.eventRepo.all += testEvent(2, "2026-11-11")
        assertTrue(f.session.reloadGroup().isSuccess)
        assertEquals(1, f.session.state.value.children.size)
        assertEquals(1, f.session.state.value.events.size)
        assertEquals(profileCalls, f.profiles.myProfileCalls)
    }

    @Test fun childrenLoadFailureFailsTheGroupReload() = runTest {
        val f = started()
        f.childRepo.failure = IOException("réseau")
        assertTrue(f.session.reloadGroup().isFailure)
    }

    @Test fun signOutClearsChildrenAndEvents() = runTest {
        val f = started { seedReady(); childRepo.all += testChild(1); eventRepo.all += testEvent(1, "2026-12-25") }
        f.session.signOut()
        assertTrue(f.session.state.value.children.isEmpty())
        assertTrue(f.session.state.value.events.isEmpty())
    }

    @Test fun switchingGroupReplacesChildren() = runTest {
        val f = started { seedReady(1, 2) }
        f.childRepo.all += testChild(7)
        f.session.selectGroup(group(2))
        assertEquals(listOf(testChild(7)), f.session.state.value.children)
    }

    @Test fun itemsChangedBumpsTheRevision() = runTest {
        val f = started()
        val before = f.session.itemsRevision.value
        f.session.itemsChanged()
        assertEquals(before + 1, f.session.itemsRevision.value)
    }

    // endregion
}
