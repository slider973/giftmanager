package ch.jonathanlemaine.giftmanager.core.widget

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Ce que le widget affiche (iOS : `CountdownEntry.State`). */
sealed interface WidgetModel {
    data object SignedOut : WidgetModel

    data object NoEvent : WidgetModel

    /** [next] est dans [days] jours ; [upcoming] sont les événements qui le suivent. */
    data class Ready(val next: WidgetEvent, val days: Int, val upcoming: List<WidgetEvent>) : WidgetModel
}

object WidgetContent {
    /** Les jours sont calculés ici, au rendu : un instantané de la veille affiche le bon compte à rebours. */
    fun from(snapshot: WidgetSnapshot, today: LocalDate): WidgetModel {
        if (!snapshot.signedIn) return WidgetModel.SignedOut
        val future = snapshot.events.filter { !it.date.date.isBefore(today) }.sortedBy { it.date }
        val next = future.firstOrNull() ?: return WidgetModel.NoEvent
        return WidgetModel.Ready(next, daysUntil(next, today), future.drop(1))
    }

    fun daysUntil(event: WidgetEvent, today: LocalDate): Int = ChronoUnit.DAYS.between(today, event.date.date).toInt()

    /** Même texte qu'iOS (`daysText`). */
    fun countdownText(days: Int): String =
        when {
            days < 1 -> "Aujourd'hui !"
            days == 1 -> "Demain"
            else -> "J-$days"
        }
}
