package ch.jonathanlemaine.giftmanager.core.childmode

import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.data.wishItem
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildModeLogicTest {
    @Test fun onlyUnownedWishesAreShownInTheParentsOrder() {
        val lego = wishItem(1, title = "Lego")
        val idea = wishItem(2, title = "Idée d'un oncle", kind = WishKind.IDEA)
        val owned = wishItem(3, title = "Déjà possédé", owned = true)
        val bike = wishItem(4, title = "Vélo")
        assertEquals(listOf("Lego", "Vélo"), ChildModeLogic.visibleItems(listOf(lego, idea, owned, bike)).map { it.title })
    }

    @Test fun itemsCarryOnlyTheHeartAndNeverAStatusOrAReservation() {
        val reserved = wishItem(1, title = "Lego", priority = 1, status = ItemStatus.TAKEN, myReservation = ReservationState.RESERVED)
            .copy(imageUrl = "https://example.test/lego.png", notes = "Note secrète")
        val visible = ChildModeLogic.visibleItems(listOf(reserved))
        assertEquals(listOf(ChildModeItem(reserved.id, "Lego", "https://example.test/lego.png", true)), visible)
    }

    @Test fun childModeItemHasExactlyFourFields() {
        val fields = ChildModeItem::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(setOf("id", "title", "imageUrl", "isFavorite"), fields)
    }

    @Test fun favoriteCountCountsHearts() {
        val items = ChildModeLogic.visibleItems(listOf(wishItem(1, priority = 1), wishItem(2), wishItem(3, priority = 1)))
        assertEquals(2, ChildModeLogic.favoriteCount(items))
        assertEquals(0, ChildModeLogic.favoriteCount(emptyList()))
    }

    @Test fun exitNeedsThreeSecondsOfHold() {
        assertEquals(3000L, ExitHold.HOLD_MILLIS)
        assertEquals(0f, ExitHold.progress(0), 0f)
        assertEquals(0.5f, ExitHold.progress(1500), 0.0001f)
        assertEquals(1f, ExitHold.progress(3000), 0f)
        assertEquals(1f, ExitHold.progress(9000), 0f)
        assertEquals(0f, ExitHold.progress(-5), 0f)
        assertFalse(ExitHold.isComplete(2999))
        assertTrue(ExitHold.isComplete(3000))
    }
}
