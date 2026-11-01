package ch.jonathanlemaine.giftmanager.push

import ch.jonathanlemaine.giftmanager.core.prices.PriceAlertPayload
import java.util.UUID

/** Écran que le toucher d'une notification doit ouvrir. */
sealed interface NotificationTarget {
    /** Fiche d'un cadeau. */
    data class GiftDetail(val itemId: UUID) : NotificationTarget

    /** Onglet « Mes achats ». */
    data object MyPurchases : NotificationTarget

    /** Remerciements reçus pour les cadeaux offerts (onglet « Mes achats »). */
    data object ThanksInbox : NotificationTarget

    /** Fiche d'un événement. */
    data class Event(val eventId: UUID) : NotificationTarget

    /** Liste de cadeaux d'un enfant. */
    data class ChildList(val childId: UUID) : NotificationTarget

    /** Accueil : cible de repli, jamais une erreur. */
    data object Home : NotificationTarget

    /**
     * Clés à poser dans l'intention de la notification (texte uniquement, pour rester testable hors Android) ;
     * [NotificationRouter.fromExtras] fait le chemin inverse.
     */
    fun toExtras(): Map<String, String> = when (this) {
        is GiftDetail -> mapOf(NotificationRouter.EXTRA_TARGET to NotificationRouter.GIFT, NotificationRouter.EXTRA_ID to itemId.toString())
        MyPurchases -> mapOf(NotificationRouter.EXTRA_TARGET to NotificationRouter.PURCHASES)
        ThanksInbox -> mapOf(NotificationRouter.EXTRA_TARGET to NotificationRouter.THANKS)
        is Event -> mapOf(NotificationRouter.EXTRA_TARGET to NotificationRouter.EVENT, NotificationRouter.EXTRA_ID to eventId.toString())
        is ChildList -> mapOf(NotificationRouter.EXTRA_TARGET to NotificationRouter.CHILD, NotificationRouter.EXTRA_ID to childId.toString())
        Home -> mapOf(NotificationRouter.EXTRA_TARGET to NotificationRouter.HOME)
    }
}

/**
 * Routage des notifications push (#76) — iOS : `NotificationRouter`. La convention du `data` FCM est celle de
 * `supabase/functions/_shared/push.ts` :
 *
 * | type          | clé       | cible                                                              |
 * |---------------|-----------|--------------------------------------------------------------------|
 * | `price`       | `item_id` | fiche du cadeau                                                    |
 * | `purchase`    | —         | Mes achats (les rappels d'achat concernent mes réservations)       |
 * | `birthday`    | `event_id`| fiche de l'événement                                               |
 * | `new-items`   | `child_id`| liste de l'enfant                                                  |
 * | `thanks`      | —         | boîte des remerciements (voir ci-dessous)                          |
 * | `idea-review` | `item_id` | fiche du cadeau (mon idée)                                         |
 * | `reservation` | —         | Accueil : la notification est anonyme, elle ne désigne aucun cadeau |
 *
 * Un merci est envoyé aux **donateurs** (ceux qui ont réservé ou participé à la cagnotte) : ce qu'ils attendent est
 * le message et la photo du parent, qui vivent dans la boîte des remerciements, pas la fiche du cadeau (que le
 * donateur ne voit que comme une ligne de « Mes achats »).
 *
 * Tolérant : un type inconnu, une clé manquante ou un UUID invalide ouvrent l'Accueil, jamais d'exception.
 */
object NotificationRouter {
    const val EXTRA_TARGET = "gm_push_target"
    const val EXTRA_ID = "gm_push_id"

    internal const val GIFT = "gift"
    internal const val PURCHASES = "purchases"
    internal const val THANKS = "thanks"
    internal const val EVENT = "event"
    internal const val CHILD = "child"
    internal const val HOME = "home"

    const val KEY_TYPE = "type"
    const val KEY_EVENT_ID = "event_id"
    const val KEY_CHILD_ID = "child_id"

    fun parse(data: Map<String, String>): NotificationTarget =
        when (PushType.from(data[KEY_TYPE])) {
            PushType.Price, PushType.IdeaReview ->
                PriceAlertPayload.itemId(data)?.let(NotificationTarget::GiftDetail) ?: NotificationTarget.Home
            PushType.Purchase -> NotificationTarget.MyPurchases
            PushType.Birthday -> uuid(data[KEY_EVENT_ID])?.let(NotificationTarget::Event) ?: NotificationTarget.Home
            PushType.NewItems -> uuid(data[KEY_CHILD_ID])?.let(NotificationTarget::ChildList) ?: NotificationTarget.Home
            PushType.Thanks -> NotificationTarget.ThanksInbox
            PushType.Reservation, null -> NotificationTarget.Home
        }

    /** Cible stockée par [NotificationTarget.toExtras] ; `null` si l'intention ne vient pas d'une notification. */
    fun fromExtras(extras: Map<String, String?>): NotificationTarget? {
        val kind = extras[EXTRA_TARGET] ?: return null
        val id = uuid(extras[EXTRA_ID])
        return when (kind) {
            GIFT -> id?.let(NotificationTarget::GiftDetail)
            EVENT -> id?.let(NotificationTarget::Event)
            CHILD -> id?.let(NotificationTarget::ChildList)
            PURCHASES -> NotificationTarget.MyPurchases
            THANKS -> NotificationTarget.ThanksInbox
            else -> null
        } ?: NotificationTarget.Home
    }

    private fun uuid(value: String?): UUID? =
        try {
            value?.let(UUID::fromString)
        } catch (_: IllegalArgumentException) {
            null
        }
}
