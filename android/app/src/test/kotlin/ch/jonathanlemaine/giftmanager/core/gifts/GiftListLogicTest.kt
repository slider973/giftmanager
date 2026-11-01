package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.testEvent
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.wishItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GiftListLogicTest {
    private val wish1 = wishItem(1, position = 0)
    private val wish2 = wishItem(2, position = 1)
    private val wish3 = wishItem(3, position = 2)
    private val owned = wishItem(4, owned = true)
    private val idea = wishItem(5, kind = WishKind.IDEA)
    private val all = listOf(wish1, wish2, wish3, owned, idea)

    @Test fun partitionIntoWishesOwnedAndIdeas() {
        assertEquals(listOf(wish1, wish2, wish3), GiftListLogic.wishes(all))
        assertEquals(listOf(owned), GiftListLogic.owned(all))
        assertEquals(listOf(idea), GiftListLogic.ideas(all))
        assertEquals(GiftCounts(3, 1, 1), GiftListLogic.counts(all))
        assertEquals(listOf(idea), GiftListLogic.items(all, GiftTab.IDEAS))
    }

    @Test fun ideasTabIsHiddenFromParents() {
        assertEquals(listOf(GiftTab.WISHES, GiftTab.OWNED), GiftListLogic.tabs(isParent = true))
        assertEquals(GiftTab.entries, GiftListLogic.tabs(isParent = false))
    }

    @Test fun moveRenumbersWishesAndKeepsTheOthers() {
        val moved = GiftListLogic.move(all, from = 0, to = 2)!!
        assertEquals(listOf(wish2.id, wish3.id, wish1.id), moved.orderedIds)
        assertEquals(listOf(0, 1, 2), moved.items.take(3).map { it.position })
        assertEquals(listOf(owned, idea), moved.items.drop(3))
    }

    @Test fun moveOutOfRangeDoesNothing() {
        assertNull(GiftListLogic.move(all, from = 0, to = 3))
        assertNull(GiftListLogic.move(all, from = -1, to = 0))
    }

    @Test fun positionsOfWishesOnly() {
        assertEquals(mapOf(wish1.id to 0, wish2.id to 1, wish3.id to 2), GiftListLogic.positions(all))
    }

    @Test fun parentEditsWishesButNotIdeas() {
        assertTrue(GiftListLogic.canEdit(wish1, isParent = true, userId = uuid(1)))
        assertFalse(GiftListLogic.canEdit(wish1, isParent = false, userId = uuid(1)))
        assertFalse(GiftListLogic.canEdit(idea, isParent = true, userId = uuid(1)))
    }

    @Test fun ideaIsEditedByItsAuthorOnly() {
        val mine = wishItem(6, kind = WishKind.IDEA, createdBy = uuid(1))
        assertTrue(GiftListLogic.canEdit(mine, isParent = false, userId = uuid(1)))
        assertFalse(GiftListLogic.canEdit(mine, isParent = false, userId = uuid(2)))
        assertFalse(GiftListLogic.canEdit(mine, isParent = false, userId = null))
        assertFalse(GiftListLogic.canEdit(wishItem(7, kind = WishKind.IDEA, createdBy = null), isParent = false, userId = null))
    }

    @Test fun nothingIsEditableOnAPastEvent() {
        assertFalse(GiftListLogic.canEdit(wish1, isParent = true, userId = uuid(1), readOnly = true))
        assertFalse(GiftListLogic.canParentReserve(wish1, isParent = true, readOnly = true))
        assertFalse(GiftListLogic.canToggleFavorite(wish1, isParent = true, readOnly = true))
        assertFalse(GiftListLogic.canReorder(true, GiftTab.WISHES, all, readOnly = true))
        assertNull(GiftListLogic.addMode(true, GiftTab.WISHES, readOnly = true))
    }

    @Test fun parentCanReserveOnlyAnUnreservedWish() {
        assertTrue(GiftListLogic.canParentReserve(wish1, isParent = true))
        assertFalse(GiftListLogic.canParentReserve(wish1, isParent = false))
        assertFalse(GiftListLogic.canParentReserve(owned, isParent = true))
        assertFalse(GiftListLogic.canParentReserve(idea, isParent = true))
        assertFalse(GiftListLogic.canParentReserve(wishItem(8, myReservation = ReservationState.RESERVED), isParent = true))
    }

    @Test fun togglesAreForParentsOnWishes() {
        assertTrue(GiftListLogic.canToggleOwned(wish1, isParent = true))
        assertTrue(GiftListLogic.canToggleOwned(owned, isParent = true))
        assertFalse(GiftListLogic.canToggleOwned(idea, isParent = true))
        assertFalse(GiftListLogic.canToggleOwned(wish1, isParent = false))
        assertTrue(GiftListLogic.canToggleFavorite(wish1, isParent = true))
        assertFalse(GiftListLogic.canToggleFavorite(owned, isParent = true))
    }

    @Test fun reorderNeedsAParentTheWishesTabAndTwoWishes() {
        assertTrue(GiftListLogic.canReorder(true, GiftTab.WISHES, all))
        assertFalse(GiftListLogic.canReorder(false, GiftTab.WISHES, all))
        assertFalse(GiftListLogic.canReorder(true, GiftTab.OWNED, all))
        assertFalse(GiftListLogic.canReorder(true, GiftTab.WISHES, listOf(wish1, owned)))
    }

    @Test fun addModePerTab() {
        assertEquals(AddMode(WishKind.WISH, owned = false), GiftListLogic.addMode(true, GiftTab.WISHES))
        assertEquals(AddMode(WishKind.WISH, owned = true), GiftListLogic.addMode(true, GiftTab.OWNED))
        assertEquals(AddMode(WishKind.IDEA, owned = false), GiftListLogic.addMode(false, GiftTab.IDEAS))
        assertNull(GiftListLogic.addMode(false, GiftTab.WISHES))
        assertNull(GiftListLogic.addMode(true, GiftTab.IDEAS))
    }

    @Test fun selectableEventsAreGlobalOrTheChildsOwn() {
        val christmas = testEvent(1, "2026-12-25", kind = GiftEventKind.CHRISTMAS)
        val mine = testEvent(2, "2026-03-12", kind = GiftEventKind.BIRTHDAY, childId = uuid(401))
        val other = testEvent(3, "2026-05-01", kind = GiftEventKind.BIRTHDAY, childId = uuid(402))
        assertEquals(listOf(mine, christmas), GiftListLogic.selectableEvents(listOf(christmas, other, mine), uuid(401)))
    }

    @Test fun parentListFollowsTheSessionThenTheServer() {
        assertTrue(GiftListLogic.isParentList(true, listOf(wish1)))
        assertFalse(GiftListLogic.isParentList(false, listOf(wishItem(1, status = null))))
        // Enfant hors famille : le serveur tranche.
        assertTrue(GiftListLogic.isParentList(null, listOf(wishItem(1, status = null))))
        assertFalse(GiftListLogic.isParentList(null, listOf(wishItem(1, status = null), wish2)))
        assertTrue(GiftListLogic.isParentList(null, emptyList()))
    }
}
