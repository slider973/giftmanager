package ch.jonathanlemaine.giftmanager.core.prices

import java.util.UUID

/**
 * Lecture de la charge utile d'une alerte de prix (#39) — iOS : `NotificationRouter.itemId`. La fonction Edge place
 * l'identifiant du cadeau dans `item_id` ; le routage des notifications (#76) l'utilise pour ouvrir la fiche.
 */
object PriceAlertPayload {
    const val ITEM_ID_KEY = "item_id"

    /** Cadeau visé par l'alerte ; `null` si la clé manque ou n'est pas un UUID (jamais d'exception). */
    fun itemId(data: Map<String, String>): UUID? =
        try {
            data[ITEM_ID_KEY]?.let(UUID::fromString)
        } catch (_: IllegalArgumentException) {
            null
        }
}
