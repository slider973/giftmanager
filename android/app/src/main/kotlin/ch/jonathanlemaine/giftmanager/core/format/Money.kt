package ch.jonathanlemaine.giftmanager.core.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    private val frFr = Locale.Builder().setLanguage("fr").setRegion("FR").build()
    private val frCh = Locale.Builder().setLanguage("fr").setRegion("CH").build()

    /** Montant formaté à la française (« fr_CH » pour le franc suisse), EUR par défaut. */
    fun format(amount: BigDecimal?, currency: String?): String? {
        if (amount == null) return null
        val code = currency ?: "EUR"
        val format = NumberFormat.getCurrencyInstance(if (code == "CHF") frCh else frFr)
        return try {
            format.currency = Currency.getInstance(code)
            format.format(amount)
        } catch (_: IllegalArgumentException) {
            // Code de devise inconnu : on affiche le montant avec son code plutôt que d'échouer.
            "${amount.toPlainString()} $code"
        }
    }
}
