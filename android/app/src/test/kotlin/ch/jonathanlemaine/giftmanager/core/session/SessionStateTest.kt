package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.model.HouseholdGroup
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {
    private val me = uuid(1)
    private val base = SessionState(
        phase = SessionPhase.Ready,
        userId = me,
        groups = listOf(group(1), group(2), group(3)),
        currentGroup = group(1),
        households = listOf(household(1), household(2)),
        householdMembers = listOf(HouseholdMember(household(1).id, me), HouseholdMember(household(2).id, uuid(2))),
        householdGroups = listOf(HouseholdGroup(household(1).id, group(1).id), HouseholdGroup(household(1).id, group(3).id)),
    )

    @Test fun myHouseholdFollowsMembership() {
        assertEquals(household(1), base.myHousehold)
        assertNull(base.copy(userId = uuid(9)).myHousehold)
        assertNull(base.copy(userId = null).myHousehold)
    }

    @Test fun familiesSharingMyHouseholdKeepGroupOrder() {
        assertEquals(listOf(group(1), group(3)), base.familiesSharingMyHousehold)
        assertTrue(base.copy(householdMembers = emptyList()).familiesSharingMyHousehold.isEmpty())
    }

    @Test fun canShareOnlyWhereNotYetShared() {
        assertFalse(base.canShareHouseholdWithCurrentGroup)
        assertTrue(base.copy(currentGroup = group(2)).canShareHouseholdWithCurrentGroup)
        assertFalse(base.copy(currentGroup = null).canShareHouseholdWithCurrentGroup)
        assertFalse(base.copy(householdMembers = emptyList()).canShareHouseholdWithCurrentGroup)
    }

    @Test fun parentsAreMembersOfTheHousehold() {
        val s = base.copy(members = listOf(profile(me, "Jo"), profile(uuid(2), "Marie"), profile(uuid(3), "Paul")))
        assertEquals(listOf("Jo"), s.parents(household(1)).map { it.displayName })
        assertEquals(listOf("Marie"), s.parents(household(2)).map { it.displayName })
    }
}
