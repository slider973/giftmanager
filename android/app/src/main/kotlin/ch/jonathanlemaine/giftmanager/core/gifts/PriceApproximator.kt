package ch.jonathanlemaine.giftmanager.core.gifts

import java.math.BigDecimal

/**
 * Prix converti indicatif (« ≈ 214 € »), posé après le prix réel. `null` : rien à afficher (même devise,
 * taux inconnus…). Les taux de change arrivent avec #74.
 */
fun interface PriceApproximator {
    fun approxText(amount: BigDecimal?, from: String?, to: String?): String?
}

/** Implémentation par défaut tant que les taux ne sont pas disponibles. */
object NoPriceApproximation : PriceApproximator {
    override fun approxText(amount: BigDecimal?, from: String?, to: String?): String? = null
}
