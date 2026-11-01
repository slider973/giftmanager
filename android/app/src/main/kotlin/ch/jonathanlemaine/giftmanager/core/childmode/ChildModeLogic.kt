package ch.jonathanlemaine.giftmanager.core.childmode

import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import java.util.UUID

/**
 * Ce que l'enfant voit d'un souhait : une photo, un nom, un cœur.
 *
 * Volontairement sans prix, statut, réservation, cagnotte ni notes : ce qui n'existe pas dans ce type ne peut pas
 * s'afficher par erreur en mode enfant (#36). Un test de réflexion en garde la liste des champs.
 */
data class ChildModeItem(
    val id: UUID,
    val title: String,
    val imageUrl: String?,
    val isFavorite: Boolean,
)

/** Règles du mode enfant (iOS : `ChildModeModel`). Fonctions pures. */
object ChildModeLogic {
    /**
     * Seuls les souhaits de la liste : jamais les idées des autres adultes (secrètes pour les parents) ni les jouets
     * déjà possédés. L'ordre choisi par les parents est conservé.
     */
    fun visibleItems(items: List<WishItem>): List<ChildModeItem> =
        items.filter { it.kind == WishKind.WISH && !it.owned }
            .map { ChildModeItem(it.id, it.title, it.imageUrl, it.isFavorite) }

    fun favoriteCount(items: List<ChildModeItem>): Int = items.count { it.isFavorite }
}

/** Sortie protégée : il faut maintenir le bouton « Quitter » [HOLD_MILLIS] ms (iOS : `ChildModeExitButton`). */
object ExitHold {
    const val HOLD_MILLIS = 3000L

    /** Avancement de l'anneau, de 0 à 1, après [elapsedMillis] ms d'appui continu. */
    fun progress(elapsedMillis: Long): Float = (elapsedMillis.toFloat() / HOLD_MILLIS).coerceIn(0f, 1f)

    fun isComplete(elapsedMillis: Long): Boolean = elapsedMillis >= HOLD_MILLIS
}
