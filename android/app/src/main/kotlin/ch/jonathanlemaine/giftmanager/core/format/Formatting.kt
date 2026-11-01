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

/** Couleurs d'avatar proposées pour les enfants (noms des tokens pastel du design system). */
object AvatarPalette {
    val colors: List<String> = listOf("pastelPink", "pastelMint", "pastelBlue", "pastelPeach", "pastelLavender")
    val emojis: List<String> = listOf("🦖", "🦄", "⚽️", "🐻", "🐱", "🚀", "🌸", "🦊", "🐼", "🎨", "🏰", "🧸")
}
