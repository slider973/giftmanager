package ch.jonathanlemaine.giftmanager.core.widget

import ch.jonathanlemaine.giftmanager.core.session.SessionPhase
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.upcomingEvents
import java.time.Clock

object WidgetSnapshotBuilder {
    /**
     * Instantané à afficher pour cet état de session, ou `null` s'il faut garder le précédent : démarrage en cours
     * ou chargement échoué (on ne vide pas le widget pour une panne réseau).
     */
    fun build(state: SessionState, clock: Clock = Clock.systemDefaultZone()): WidgetSnapshot? {
        if (state.phase == SessionPhase.Launching || state.loadFailed) return null
        if (state.phase == SessionPhase.SignedOut) return WidgetSnapshot(signedIn = false)
        val events = state.upcomingEvents(clock).take(WidgetSnapshot.MAX_EVENTS).map { WidgetEvent(it.kind, it.title, it.eventDate) }
        return WidgetSnapshot(signedIn = true, events = events)
    }
}
