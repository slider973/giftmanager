package ch.jonathanlemaine.giftmanager.core.currency

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Montants indicatifs (« ≈ 147 € ») — `IndicativePrice` (`ExchangeRates.swift`). */
object IndicativePrice {
    private val frFr = Locale.Builder().setLanguage("fr").setRegion("FR").build()
    private val frCh = Locale.Builder().setLanguage("fr").setRegion("CH").build()
    private val TEN = BigDecimal.TEN

    private fun digits(value: BigDecimal) = if (value.abs() < TEN) 2 else 0

    /** Arrondi d'affichage : à l'unité dès 10, au centime en dessous (un ≈ 4,80 € reste utile). */
    fun rounded(value: BigDecimal): BigDecimal = value.setScale(digits(value), RoundingMode.HALF_UP)

    /** Montant seul, sans le « ≈ » : « 147 € », « 214 CHF ». */
    fun amountText(value: BigDecimal, currency: String): String {
        val code = currency.uppercase()
        val digits = digits(value)
        val format = NumberFormat.getCurrencyInstance(if (code == "CHF") frCh else frFr)
        return try {
            format.currency = Currency.getInstance(code)
            format.minimumFractionDigits = digits
            format.maximumFractionDigits = digits
            format.roundingMode = RoundingMode.HALF_UP
            format.format(value)
        } catch (_: IllegalArgumentException) {
            // Code de devise inconnu : le montant arrondi avec son code plutôt qu'une erreur.
            "${rounded(value).toPlainString()} $code"
        }
    }

    /** « ≈ 147 € ». */
    fun text(value: BigDecimal, currency: String): String = "≈ " + amountText(value, currency)

    /** Lecture TalkBack : « environ 147 €, conversion indicative ». */
    fun spokenText(value: BigDecimal, currency: String): String = "environ ${amountText(value, currency)}, conversion indicative"
}
