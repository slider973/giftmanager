package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.PotAmount
import ch.jonathanlemaine.giftmanager.core.model.PotProgress
import java.math.BigDecimal

/** Règles de la feuille « Participer à une cagnotte » (iOS : `JoinPotView`). */
object PotJoinLogic {
    const val FALLBACK_CURRENCY = "CHF"

    /**
     * Devise de départ : celle de la cagnotte existante (qui la verrouille), sinon celle du premier lien qui a un prix,
     * sinon celle du profil, sinon CHF.
     */
    fun defaultCurrency(locked: String?, links: List<ItemLink>, profileCurrency: String?): String =
        locked
            ?: links.firstOrNull { it.currency != null && it.price != null }?.currency
            ?: profileCurrency
            ?: FALLBACK_CURRENCY

    /**
     * Reste à réunir, hors ma part actuelle (que je remplace en la modifiant). Pour une nouvelle cagnotte, c'est le
     * prix du cadeau dans la devise choisie. `null` : aucun prix de référence.
     */
    fun remainingForMe(progress: PotProgress?, myContribution: BigDecimal?, currency: String, links: List<ItemLink>): BigDecimal? {
        if (progress != null) {
            val remaining = progress.remaining ?: return null
            return remaining + (myContribution ?: BigDecimal.ZERO)
        }
        return PotProgress.target(currency, links)
    }

    /** Montants proposés en un geste, le reste à réunir en dernier s'il est connu. */
    fun suggestions(remaining: BigDecimal?): List<BigDecimal> = PotAmount.suggestions(remaining)
}
