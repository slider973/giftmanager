package ch.jonathanlemaine.giftmanager.core.currency

import ch.jonathanlemaine.giftmanager.core.gifts.PriceApproximator
import java.math.BigDecimal

/**
 * Prix converti indicatif à partir des taux courants (`CurrencyService` iOS, partie calcul). Sans taux, sans prix,
 * avec une devise inconnue ou identique : `null`, on ne devine jamais.
 */
class RatesApproximator(private val rates: ExchangeRates?) : PriceApproximator {
    /**
     * Montant converti dans [to], ou `null` s'il n'y a rien à convertir (pas de prix, même devise, devise
     * d'origine ou cible inconnue, taux absent).
     */
    fun convert(amount: BigDecimal?, from: String?, to: String?): BigDecimal? {
        if (amount == null || from == null || to == null || rates == null) return null
        if (from.uppercase() == to.uppercase()) return null
        return rates.convert(amount, from, to)
    }

    /** Au moins un prix est-il converti ? (pour afficher la mention « conversion indicative »). */
    fun hasConversion(prices: List<Pair<BigDecimal?, String?>>, to: String?): Boolean =
        prices.any { (amount, currency) -> convert(amount, currency, to) != null }

    /** « ≈ 147 € » à côté d'un prix dans une autre devise que celle du profil. */
    override fun approxText(amount: BigDecimal?, from: String?, to: String?): String? {
        val target = to ?: return null
        return convert(amount, from, target)?.let { IndicativePrice.text(it, target) }
    }

    fun spokenText(amount: BigDecimal?, from: String?, to: String?): String? {
        val target = to ?: return null
        return convert(amount, from, target)?.let { IndicativePrice.spokenText(it, target) }
    }

    /**
     * Total converti de plusieurs devises. `null` si tout est déjà dans la devise cible, ou si un taux manque
     * (un total partiel serait trompeur).
     */
    fun convertedTotal(totals: List<Pair<String, BigDecimal>>, to: String?): BigDecimal? {
        if (to == null || rates == null || totals.isEmpty()) return null
        if (totals.none { it.first.uppercase() != to.uppercase() }) return null
        var sum = BigDecimal.ZERO
        for ((currency, amount) in totals) sum += rates.convert(amount, currency, to) ?: return null
        return sum
    }
}
