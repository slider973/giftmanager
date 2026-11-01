package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.data.wishItem
import ch.jonathanlemaine.giftmanager.ui.components.GiftStatus
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Règles d'état de la fiche cadeau (`GiftDetailModelTests.swift`) et confidentialité : un parent ne doit jamais
 * apprendre, par un statut, un badge ou un bouton, que son enfant reçoit tel cadeau.
 */
class GiftActionReducerTest {
    private val available = wishItem(1)
    private val parentView = wishItem(1, status = null)

    @Test fun reservingMakesTheGiftMine() {
        val next = GiftActionReducer.reserved(available, isParent = false)
        assertEquals(ItemStatus.MINE, next.status)
        assertEquals(ReservationState.RESERVED, next.myReservation)
    }

    @Test fun parentNeverGetsAStatusAfterReserving() {
        assertNull(GiftActionReducer.reserved(parentView, isParent = true).status)
        assertEquals(ReservationState.RESERVED, GiftActionReducer.reserved(parentView, isParent = true).myReservation)
    }

    @Test fun cancellingFreesTheGiftForMembersOnly() {
        val mine = wishItem(1, status = ItemStatus.MINE, myReservation = ReservationState.RESERVED)
        assertEquals(ItemStatus.AVAILABLE, GiftActionReducer.cancelled(mine, isParent = false).status)
        assertNull(GiftActionReducer.cancelled(mine, isParent = false).myReservation)
        // Afficher « disponible » à un parent lui apprendrait que le cadeau vient d'être libéré, donc réservé.
        assertNull(GiftActionReducer.cancelled(mine, isParent = true).status)
    }

    @Test fun purchasedAndBack() {
        val reserved = wishItem(1, myReservation = ReservationState.RESERVED)
        assertEquals(ReservationState.PURCHASED, GiftActionReducer.purchased(reserved, true).myReservation)
        assertEquals(ReservationState.RESERVED, GiftActionReducer.purchased(reserved, false).myReservation)
    }

    @Test fun unavailableMeansTakenButNeverForParents() {
        assertEquals(ItemStatus.TAKEN, GiftActionReducer.unavailable(available, isParent = false).status)
        assertNull(GiftActionReducer.unavailable(available, isParent = true).status)
        assertNull(GiftActionReducer.unavailable(available, isParent = false).myReservation)
    }

    @Test fun togglesFlipTheFlag() {
        assertTrue(GiftActionReducer.favoriteToggled(wishItem(1, priority = 0)).isFavorite)
        assertFalse(GiftActionReducer.favoriteToggled(wishItem(1, priority = 1)).isFavorite)
        assertTrue(GiftActionReducer.ownedToggled(available).owned)
        assertFalse(GiftActionReducer.ownedToggled(wishItem(1, owned = true)).owned)
    }

    // region Confidentialité

    @Test fun nullStatusNeverProducesABadgeOrReservationAction() {
        for (isParentView in listOf(true, false)) {
            val badge = GiftActionReducer.badge(parentView, isParentView)
            assertNull(badge)
        }
        val actions = GiftActionReducer.availability(parentView, isParentView = true)
        assertNull(actions.badge)
        assertTrue(actions.surpriseNotice)
        assertFalse(actions.canReserve)
        assertFalse(actions.canStartPot)
        assertFalse(actions.showPot)
        assertFalse(actions.canMarkPurchased)
        assertFalse(actions.canCancelReservation)
        assertFalse(actions.isTaken)
        assertFalse(actions.isOwnedByChild)
    }

    @Test fun parentViewHidesEverythingEvenIfTheServerSentAStatus() {
        // Défense en profondeur : même un statut inattendu ne s'affiche pas en vue parent.
        for (status in ItemStatus.entries) {
            val item = wishItem(1, status = status)
            val actions = GiftActionReducer.availability(item, isParentView = true)
            assertNull(GiftActionReducer.badge(item, isParentView = true))
            assertNull(actions.badge)
            assertFalse(actions.canReserve)
            assertFalse(actions.canStartPot)
            assertFalse(actions.showPot)
            assertFalse(actions.isTaken)
        }
    }

    @Test fun parentCanStillManageHisOwnReservation() {
        val own = wishItem(1, status = null, myReservation = ReservationState.RESERVED)
        val actions = GiftActionReducer.availability(own, isParentView = true)
        assertTrue(actions.canMarkPurchased)
        assertTrue(actions.canCancelReservation)
        assertFalse(actions.canReserve)
        assertTrue(GiftActionReducer.availability(wishItem(1, status = null, myReservation = ReservationState.PURCHASED), true).canUnmarkPurchased)
        // Une idée ou un cadeau déjà possédé n'a pas de réservation à gérer.
        assertFalse(GiftActionReducer.availability(wishItem(1, status = null, kind = WishKind.IDEA, myReservation = ReservationState.RESERVED), true).canCancelReservation)
    }

    @Test fun memberActionsFollowTheServerStatus() {
        val actions = { status: ItemStatus, reservation: ReservationState? ->
            GiftActionReducer.availability(wishItem(1, status = status, myReservation = reservation), isParentView = false)
        }
        assertEquals(GiftStatus.AVAILABLE, actions(ItemStatus.AVAILABLE, null).badge)
        assertTrue(actions(ItemStatus.AVAILABLE, null).canReserve)
        assertTrue(actions(ItemStatus.AVAILABLE, null).canStartPot)
        assertFalse(actions(ItemStatus.AVAILABLE, null).surpriseNotice)
        assertTrue(actions(ItemStatus.POT, null).showPot)
        assertFalse(actions(ItemStatus.POT, null).canReserve)
        assertTrue(actions(ItemStatus.MINE, ReservationState.RESERVED).canMarkPurchased)
        assertTrue(actions(ItemStatus.MINE, ReservationState.PURCHASED).canUnmarkPurchased)
        assertTrue(actions(ItemStatus.MINE, ReservationState.PURCHASED).canCancelReservation)
        assertTrue(actions(ItemStatus.TAKEN, null).isTaken)
        assertFalse(actions(ItemStatus.TAKEN, null).canReserve)
        assertTrue(actions(ItemStatus.OWNED, null).isOwnedByChild)
        assertEquals(GiftStatus.OWNED, actions(ItemStatus.OWNED, null).badge)
    }

    @Test fun parentViewDefaultsToTheServerWhenTheChildIsUnknown() {
        // Fiche ouverte par son seul identifiant, enfant hors famille courante.
        assertTrue(GiftActionReducer.isParentView(null, parentView))
        assertFalse(GiftActionReducer.isParentView(null, available))
        // Quand la session connaît l'enfant, elle tranche.
        assertTrue(GiftActionReducer.isParentView(true, available))
        assertFalse(GiftActionReducer.isParentView(false, parentView))
    }

    @Test fun potFieldsOfAParentViewNeverCountAsAProgress() {
        val item = wishItem(1, status = null, myContribution = BigDecimal.TEN)
        assertTrue(item.isInMyPot)
        assertFalse(GiftActionReducer.availability(item, isParentView = true).showPot)
    }

    // endregion
}
