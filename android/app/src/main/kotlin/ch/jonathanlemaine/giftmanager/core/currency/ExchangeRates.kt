@file:UseSerializers(DecimalSerializer::class)

package ch.jonathanlemaine.giftmanager.core.currency

import ch.jonathanlemaine.giftmanager.core.model.DecimalSerializer
import ch.jonathanlemaine.giftmanager.core.model.InstantSerializer
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Conversion de devises indicative (#38, #74) — `Services/ExchangeRates.swift`.
//
// Taux quotidiens de la BCE (via Frankfurter, gratuit et sans clé), mis en cache sur l'appareil : hors ligne,
// le dernier taux connu reste utilisé. La conversion ne remplace jamais le prix d'origine : elle s'affiche à côté,
// précédée de « ≈ » et marquée indicative.

/** Taux de change d'une journée, exprimés pour 1 unité de la devise de base. */
@Serializable
data class ExchangeRates(
    /** Devise de base (EUR pour la BCE). */
    val base: String,
    /** Jour de publication des taux, « yyyy-MM-dd ». */
    val date: String,
    /** Unités de chaque devise pour 1 unité de [base]. */
    val rates: Map<String, BigDecimal>,
    /** Moment où l'appareil a récupéré ces taux. */
    @Serializable(with = InstantSerializer::class) val fetchedAt: Instant,
) {
    fun rate(currency: String): BigDecimal? {
        val code = currency.uppercase()
        if (code == base.uppercase()) return BigDecimal.ONE
        return rates[code]?.takeIf { it.signum() > 0 }
    }

    /** Montant converti, ou `null` si l'un des deux taux manque. Même devise : montant inchangé. */
    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? {
        if (from.uppercase() == to.uppercase()) return amount
        val fromRate = rate(from) ?: return null
        val toRate = rate(to) ?: return null
        return amount.divide(fromRate, MathContext.DECIMAL128).multiply(toRate, MathContext.DECIMAL128)
    }

    /** Date de publication lisible (« 2 octobre »), pour la mention « taux BCE du … ». */
    val publicationText: String?
        get() = try {
            LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE))
        } catch (_: DateTimeParseException) {
            null
        }
}
