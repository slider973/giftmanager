package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import java.util.UUID

/** Onglets de la liste d'un enfant : Liste · Possède déjà · Idées. */
enum class GiftTab { WISHES, OWNED, IDEAS }

data class GiftCounts(val wishes: Int, val owned: Int, val ideas: Int)

/** Nouvel ordre des souhaits après un déplacement : la liste complète et les identifiants dans l'ordre. */
data class Reordered(val items: List<WishItem>, val orderedIds: List<UUID>)

/** Ce que le bouton « + » d'un onglet crée. */
data class AddMode(val kind: WishKind, val owned: Boolean)

/** Règles de la liste de cadeaux d'un enfant (iOS : `GiftListModel` et `ChildGiftsView`), sans réseau ni écran. */
object GiftListLogic {
    fun wishes(items: List<WishItem>): List<WishItem> = items.filter { it.kind == WishKind.WISH && !it.owned }

    fun owned(items: List<WishItem>): List<WishItem> = items.filter { it.kind == WishKind.WISH && it.owned }

    fun ideas(items: List<WishItem>): List<WishItem> = items.filter { it.kind == WishKind.IDEA }

    fun items(items: List<WishItem>, tab: GiftTab): List<WishItem> = when (tab) {
        GiftTab.WISHES -> wishes(items)
        GiftTab.OWNED -> owned(items)
        GiftTab.IDEAS -> ideas(items)
    }

    fun counts(items: List<WishItem>) = GiftCounts(wishes(items).size, owned(items).size, ideas(items).size)

    /**
     * Onglets visibles. Les idées sont cachées aux parents : ce sont des surprises pour eux, et l'onglet
     * (même vide) en trahirait l'existence.
     */
    fun tabs(isParent: Boolean): List<GiftTab> =
        if (isParent) listOf(GiftTab.WISHES, GiftTab.OWNED) else GiftTab.entries

    /**
     * Déplace le souhait en [from] vers [to] (index dans la liste des souhaits). Les positions sont renumérotées
     * de 0 à n-1 ; les autres cadeaux suivent, inchangés. `null` si l'un des index est hors liste.
     */
    fun move(items: List<WishItem>, from: Int, to: Int): Reordered? {
        val wishes = wishes(items).toMutableList()
        if (from !in wishes.indices || to !in wishes.indices) return null
        wishes.add(to, wishes.removeAt(from))
        val ordered = wishes.mapIndexed { index, item -> item.copy(position = index) }
        val others = items.filter { !(it.kind == WishKind.WISH && !it.owned) }
        return Reordered(ordered + others, ordered.map { it.id })
    }

    /** Positions actuelles des souhaits, pour n'écrire que celles qui changent. */
    fun positions(items: List<WishItem>): Map<UUID, Int> = wishes(items).associate { it.id to it.position }

    /** Un souhait se modifie côté parents ; une idée, par son auteur. Jamais sur un événement passé. */
    fun canEdit(item: WishItem, isParent: Boolean, userId: UUID?, readOnly: Boolean = false): Boolean =
        !readOnly && if (item.kind == WishKind.WISH) isParent else item.createdBy != null && item.createdBy == userId

    /** Un parent peut offrir lui-même un cadeau de la liste de son enfant (action discrète, dans le menu). */
    fun canParentReserve(item: WishItem, isParent: Boolean, readOnly: Boolean = false): Boolean =
        !readOnly && isParent && item.kind == WishKind.WISH && !item.owned && item.myReservation == null

    /** « Il l'a déjà » / « Remettre dans la liste » : un souhait, côté parents. */
    fun canToggleOwned(item: WishItem, isParent: Boolean): Boolean = isParent && item.kind == WishKind.WISH

    /** « Très envie » : un souhait pas encore possédé, côté parents. */
    fun canToggleFavorite(item: WishItem, isParent: Boolean, readOnly: Boolean = false): Boolean =
        !readOnly && isParent && item.kind == WishKind.WISH && !item.owned

    /** Réordonner la liste : parents, onglet Liste, avec au moins deux souhaits. */
    fun canReorder(isParent: Boolean, tab: GiftTab, items: List<WishItem>, readOnly: Boolean = false): Boolean =
        !readOnly && isParent && tab == GiftTab.WISHES && wishes(items).size > 1

    /** Événements proposés pour une liste : ceux de tous (Noël, fêtes) et de cet enfant, par date. */
    fun selectableEvents(events: List<GiftEvent>, childId: UUID): List<GiftEvent> =
        events.filter { it.childId == null || it.childId == childId }.sortedBy { it.eventDate }

    /** Ce que l'on peut ajouter depuis [tab] : un souhait (ou un cadeau déjà possédé) côté parents, une idée sinon. */
    fun addMode(isParent: Boolean, tab: GiftTab, readOnly: Boolean = false): AddMode? = when {
        readOnly -> null
        isParent && tab == GiftTab.WISHES -> AddMode(WishKind.WISH, owned = false)
        isParent && tab == GiftTab.OWNED -> AddMode(WishKind.WISH, owned = true)
        !isParent && tab == GiftTab.IDEAS -> AddMode(WishKind.IDEA, owned = false)
        else -> null
    }

    /**
     * Liste d'un parent ? La session tranche pour un enfant de la famille courante ; sinon on se fie au serveur
     * (`status` nul pour tous les cadeaux = mode surprise). Dans le doute (liste vide), vue parent : on n'affiche rien
     * de plus.
     */
    fun isParentList(sessionSaysParent: Boolean?, items: List<WishItem>): Boolean =
        sessionSaysParent ?: items.none { it.status != null }
}
