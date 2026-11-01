package ch.jonathanlemaine.giftmanager.core.widget

import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import kotlinx.serialization.Serializable

/**
 * Ce que le widget d'écran d'accueil (#80) connaît, et rien de plus. Il est visible de tous : on n'y met que le
 * prochain événement et son compte à rebours. Surtout pas les compteurs « à acheter / achetés » du widget iOS
 * (dérivés des réservations), ni identifiant, réservation ou prix. Le test de confidentialité fige ce contenu.
 */
@Serializable
data class WidgetSnapshot(
    val signedIn: Boolean = false,
    /** Au plus [MAX_EVENTS] événements à venir, du plus proche au plus lointain. */
    val events: List<WidgetEvent> = emptyList(),
) {
    companion object {
        const val MAX_EVENTS = 3
    }
}

@Serializable
data class WidgetEvent(
    val kind: GiftEventKind,
    val title: String,
    /** « yyyy-MM-dd ». */
    val date: DayDate,
)
