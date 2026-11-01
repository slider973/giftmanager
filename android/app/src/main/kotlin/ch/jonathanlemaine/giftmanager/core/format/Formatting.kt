package ch.jonathanlemaine.giftmanager.core.format

import ch.jonathanlemaine.giftmanager.core.model.DayDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatting {
    private val longDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.Builder().setLanguage("fr").setRegion("FR").build())

    /** « 25 décembre 2026 » */
    fun dateText(day: DayDate): String = longDate.format(day.date)

    fun ageText(age: Int?): String? = age?.let { if (it <= 1) "$it an" else "$it ans" }

    fun countdownText(days: Int): String =
        when {
            days < 0 -> "Passé"
            days == 0 -> "Aujourd'hui"
            days == 1 -> "Demain"
            else -> "Dans $days jours"
        }
}

/** Pays proposés dans les sélecteurs (Suisse et France en tête). */
object Countries {
    data class Country(val code: String, val name: String) {
        val id: String get() = code

        /** Drapeau en emoji : deux indicateurs régionaux. */
        val flag: String
            get() = code.map { String(Character.toChars(REGIONAL_INDICATOR_OFFSET + it.code)) }.joinToString("")
    }

    private const val REGIONAL_INDICATOR_OFFSET = 127_397

    val all: List<Country> = listOf(
        Country("CH", "Suisse"),
        Country("FR", "France"),
        Country("BE", "Belgique"),
        Country("LU", "Luxembourg"),
        Country("DE", "Allemagne"),
        Country("AT", "Autriche"),
        Country("IT", "Italie"),
        Country("ES", "Espagne"),
        Country("PT", "Portugal"),
        Country("NL", "Pays-Bas"),
        Country("GB", "Royaume-Uni"),
        Country("IE", "Irlande"),
        Country("US", "États-Unis"),
        Country("CA", "Canada"),
    )

    val currencies: List<String> = listOf("CHF", "EUR", "GBP", "USD", "CAD")

    fun name(code: String?): String = all.firstOrNull { it.code == code }?.name ?: (code ?: "—")
}

/** Couleurs d'avatar proposées pour les enfants (noms des tokens pastel du design system). */
object AvatarPalette {
    val colors: List<String> = listOf("pastelPink", "pastelMint", "pastelBlue", "pastelPeach", "pastelLavender")
    val emojis: List<String> = listOf("🦖", "🦄", "⚽️", "🐻", "🐱", "🚀", "🌸", "🦊", "🐼", "🎨", "🏰", "🧸")
}
