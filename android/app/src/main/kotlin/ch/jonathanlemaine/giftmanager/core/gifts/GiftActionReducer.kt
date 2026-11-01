package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.ui.components.GiftStatus

/**
 * Ce que devient un cadeau après une action réussie (iOS : `GiftDetailModel`), et ce que l'écran a le droit
 * de proposer.
 *
 * Confidentialité : le serveur renvoie `status = null` aux parents (mode surprise). Un parent ne voit donc
 * jamais de statut, quelle que soit l'action : après une annulation, afficher « disponible » lui apprendrait
 * que le cadeau vient d'être libéré, donc qu'il était réservé.
 */
object GiftActionReducer {
    /**
     * Vue parent d'une fiche. Quand l'enfant est connu de la famille courante, la session tranche ; pour une
     * fiche chargée par identifiant seul (notification), l'enfant peut être hors famille : on se fie alors au
     * serveur — `status == null` signifie « mode surprise » — et, par défaut, on ne montre rien de plus.
     */
    fun isParentView(sessionSaysParent: Boolean?, item: WishItem): Boolean = sessionSaysParent ?: (item.status == null)

    fun reserved(item: WishItem, isParent: Boolean): WishItem =
        item.copy(status = if (isParent) null else ItemStatus.MINE, myReservation = ReservationState.RESERVED)

    fun cancelled(item: WishItem, isParent: Boolean): WishItem =
        item.copy(status = if (isParent) null else ItemStatus.AVAILABLE, myReservation = null)

    fun purchased(item: WishItem, purchased: Boolean): WishItem =
        item.copy(myReservation = if (purchased) ReservationState.PURCHASED else ReservationState.RESERVED)

    /** Quelqu'un d'autre a réservé entre-temps : le cadeau est pris, pas disponible. */
    fun unavailable(item: WishItem, isParent: Boolean): WishItem =
        item.copy(status = if (isParent) null else ItemStatus.TAKEN)

    fun favoriteToggled(item: WishItem): WishItem = item.copy(priority = if (item.isFavorite) 0 else 1)

    fun ownedToggled(item: WishItem): WishItem = item.copy(owned = !item.owned)

    /** Statut à afficher : toujours `null` en vue parent. */
    fun badge(item: WishItem, isParentView: Boolean): GiftStatus? = if (isParentView) null else GiftStatus.of(item)

    /** Actions de réservation proposées pour un cadeau, dérivées de `item.status` (le serveur fait foi). */
    fun availability(item: WishItem, isParentView: Boolean): ReservationActions {
        if (isParentView) {
            // Mode surprise : ni badge, ni Réserver, ni cagnotte. Seule ma propre réservation reste gérable.
            val own = item.kind == WishKind.WISH && !item.owned &&
                item.myReservation != null
            return ReservationActions(
                badge = null,
                surpriseNotice = true,
                canMarkPurchased = own && item.myReservation == ReservationState.RESERVED,
                canUnmarkPurchased = own && item.myReservation == ReservationState.PURCHASED,
                canCancelReservation = own,
            )
        }
        val mine = item.status == ItemStatus.MINE
        return ReservationActions(
            badge = GiftStatus.of(item),
            surpriseNotice = false,
            canReserve = item.status == ItemStatus.AVAILABLE,
            canStartPot = item.status == ItemStatus.AVAILABLE,
            showPot = item.status == ItemStatus.POT,
            canMarkPurchased = mine && item.myReservation != ReservationState.PURCHASED,
            canUnmarkPurchased = mine && item.myReservation == ReservationState.PURCHASED,
            canCancelReservation = mine,
            isTaken = item.status == ItemStatus.TAKEN,
            isOwnedByChild = item.status == ItemStatus.OWNED,
        )
    }
}

/**
 * Actions de réservation visibles. En vue parent, tout est faux sauf [surpriseNotice] et la gestion de sa
 * propre réservation ; un `status` nul ne produit donc jamais de bouton Réserver ni de cagnotte.
 */
data class ReservationActions(
    val badge: GiftStatus?,
    val surpriseNotice: Boolean,
    val canReserve: Boolean = false,
    /** « Participer à une cagnotte » sur un cadeau disponible. */
    val canStartPot: Boolean = false,
    /** Cagnotte en cours : progression, participation et sortie. */
    val showPot: Boolean = false,
    val canMarkPurchased: Boolean = false,
    val canUnmarkPurchased: Boolean = false,
    val canCancelReservation: Boolean = false,
    val isTaken: Boolean = false,
    val isOwnedByChild: Boolean = false,
)
