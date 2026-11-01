package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Horloge figée au 15 octobre 2026 ([Fixtures.clock]). */
class SessionSelectorsTest {
    private val me = uuid(1)
    private val clock = Fixtures.clock

    // Mon foyer (1) : Léa (enfant), Max (enfant), Moi (adulte). Foyer 2 : Zoé (enfant), Mamie (adulte).
    private val lea = testChild(1, householdN = 1, name = "Léa")
    private val max = testChild(2, householdN = 1, name = "Max")
    private val moi = testChild(3, householdN = 1, name = "Moi", adult = true)
    private val zoe = testChild(4, householdN = 2, name = "Zoé")
    private val mamie = testChild(5, householdN = 2, name = "Mamie", adult = true)

    private val state = SessionState(
        userId = me,
        households = listOf(household(1), household(2)),
        householdMembers = listOf(HouseholdMember(household(1).id, me)),
        children = listOf(moi, zoe, lea, mamie, max),
    )

    @Test fun myAndOtherChildren() {
        assertEquals(listOf("Moi", "Léa", "Max"), state.myChildren.map { it.firstName })
        assertEquals(listOf("Zoé", "Mamie"), state.otherChildren.map { it.firstName })
    }

    @Test fun withoutHouseholdEveryoneIsOther() {
        val s = state.copy(householdMembers = emptyList())
        assertTrue(s.myChildren.isEmpty())
        assertEquals(5, s.otherChildren.size)
        assertFalse(s.isParent(lea))
    }

    @Test fun isParentOnlyForMyHousehold() {
        assertTrue(state.isParent(lea))
        assertFalse(state.isParent(zoe))
    }

    @Test fun householdOfChild() {
        assertEquals(household(2), state.household(zoe))
        assertNull(state.copy(households = emptyList()).household(zoe))
    }

    @Test fun childrenOfHouseholdListChildrenThenAdults() {
        assertEquals(listOf("Léa", "Max", "Moi"), state.children(household(1)).map { it.firstName })
        assertEquals(listOf("Zoé", "Mamie"), state.children(household(2)).map { it.firstName })
    }

    @Test fun childById() {
        assertEquals(zoe, state.child(zoe.id))
        assertNull(state.child(uuid(999)))
        assertNull(state.child(null))
    }

    private val past = testEvent(1, "2026-10-14")
    private val today = testEvent(2, "2026-10-15")
    private val soon = testEvent(3, "2026-11-01")
    private val christmas = testEvent(4, "2026-12-25", kind = GiftEventKind.CHRISTMAS)
    private val older = testEvent(5, "2026-01-01")
    private val withEvents = state.copy(events = listOf(christmas, past, soon, older, today))

    @Test fun upcomingAreSortedAscendingAndIncludeToday() {
        assertEquals(listOf(today, soon, christmas), withEvents.upcomingEvents(clock))
    }

    @Test fun pastAreSortedMostRecentFirst() {
        assertEquals(listOf(past, older), withEvents.pastEvents(clock))
    }

    @Test fun currentEventIsNextChristmasElseNextEvent() {
        assertEquals(christmas, withEvents.currentEvent(clock))
        assertEquals(today, withEvents.copy(events = listOf(soon, today)).currentEvent(clock))
        assertNull(withEvents.copy(events = listOf(past)).currentEvent(clock))
    }

    @Test fun christmasInThePastIsNotCurrent() {
        val last = testEvent(6, "2025-12-25", kind = GiftEventKind.CHRISTMAS)
        assertEquals(soon, state.copy(events = listOf(last, soon)).currentEvent(clock))
    }

    @Test fun birthdayConcernsOnlyTheBirthdayChild() {
        val birthday = testEvent(7, "2026-11-10", kind = GiftEventKind.BIRTHDAY, childId = zoe.id)
        assertEquals(listOf(zoe), state.children(birthday))
    }

    @Test fun otherEventsConcernEveryList() {
        assertEquals(state.children, state.children(soon))
        assertEquals(state.children, state.children(christmas))
    }

    @Test fun authorCanDeleteTheirEvent() {
        assertTrue(state.canDelete(testEvent(8, "2026-11-01", createdBy = me)))
        assertFalse(state.canDelete(testEvent(8, "2026-11-01", createdBy = uuid(2))))
        assertFalse(state.canDelete(testEvent(8, "2026-11-01", createdBy = null)))
    }

    @Test fun parentCanDeleteTheBirthdayOfTheirChildOnly() {
        assertTrue(state.canDelete(testEvent(9, "2026-11-01", kind = GiftEventKind.BIRTHDAY, childId = lea.id, createdBy = uuid(2))))
        assertFalse(state.canDelete(testEvent(9, "2026-11-01", kind = GiftEventKind.BIRTHDAY, childId = zoe.id, createdBy = uuid(2))))
    }

    @Test fun onlyUpcomingOtherEventsAreEditable() {
        assertTrue(state.canEdit(soon, clock))
        assertTrue(state.canEdit(today, clock))
        assertFalse(state.canEdit(past, clock))
        assertFalse(state.canEdit(christmas, clock))
        assertFalse(state.canEdit(testEvent(10, "2026-11-10", kind = GiftEventKind.BIRTHDAY, childId = lea.id), clock))
    }
}
