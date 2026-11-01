package ch.jonathanlemaine.giftmanager.ui.components

import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.WishItem

/** Statut d'un cadeau tel que vu par un membre de la famille (`StatusBadge.swift`). */
enum class GiftStatus(val label: String) {
    AVAILABLE("Disponible"),
    TAKEN("Déjà pris"),
    MINE("Je l'offre"),
    OWNED("Possède déjà"),

    /** Cadeau financé à plusieurs (#35). */
    POT("Cagnotte");

    companion object {
        /**
         * Statut à afficher, comme `WishItem.displayStatus` sur iOS : le serveur renvoie `status = null`
         * aux parents (mode surprise), donc `null` = aucun badge. `owned` n'est volontairement pas lu ici,
         * sinon un parent verrait un badge qui trahit l'état du cadeau.
         */
        fun of(item: WishItem): GiftStatus? = when (item.status) {
            ItemStatus.AVAILABLE -> AVAILABLE
            ItemStatus.TAKEN -> TAKEN
            ItemStatus.MINE -> MINE
            ItemStatus.OWNED -> OWNED
            ItemStatus.POT -> POT
            null -> null
        }
    }
}
