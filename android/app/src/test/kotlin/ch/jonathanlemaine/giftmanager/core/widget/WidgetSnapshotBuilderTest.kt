package ch.jonathanlemaine.giftmanager.core.widget

import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.session.SessionPhase
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WidgetSnapshotBuilderTest {
    private val clock = Clock.fixed(Instant.parse("2026-12-01T10:00:00Z"), ZoneOffset.UTC)

    private fun event(n: Int, date: String, kind: GiftEventKind = GiftEventKind.OTHER) =
        GiftEvent(uuid(n), null, kind, "Fête $n", DayDate.parse(date))

    private fun ready(vararg events: GiftEvent) = SessionState(phase = SessionPhase.Ready, userId = uuid(1), events = events.toList())

    @Test fun signedOutClearsEverything() {
        val state = SessionState(phase = SessionPhase.SignedOut, events = listOf(event(1, "2026-12-25")))
        assertEquals(WidgetSnapshot(signedIn = false), WidgetSnapshotBuilder.build(state, clock))
    }

    @Test fun launchingKeepsThePreviousSnapshot() {
        assertNull(WidgetSnapshotBuilder.build(SessionState(phase = SessionPhase.Launching), clock))
    }

    @Test fun failedLoadKeepsThePreviousSnapshot() {
        assertNull(WidgetSnapshotBuilder.build(SessionState(phase = SessionPhase.SignedOut, loadFailed = true), clock))
    }

    @Test fun keepsUpcomingEventsSortedAndCappedAtThree() {
        val state = ready(
            event(1, "2027-03-01"), event(2, "2026-11-01"), event(3, "2026-12-25", GiftEventKind.CHRISTMAS),
            event(4, "2027-01-05", GiftEventKind.BIRTHDAY), event(5, "2026-12-02"),
        )
        val snapshot = WidgetSnapshotBuilder.build(state, clock)!!
        assertEquals(true, snapshot.signedIn)
        assertEquals(listOf("Fête 5", "Fête 3", "Fête 4"), snapshot.events.map { it.title })
        assertEquals(GiftEventKind.CHRISTMAS, snapshot.events[1].kind)
    }

    @Test fun signedInWithoutEventStaysSignedIn() {
        val state = SessionState(phase = SessionPhase.NeedsGroup, userId = uuid(1))
        assertEquals(WidgetSnapshot(signedIn = true), WidgetSnapshotBuilder.build(state, clock))
    }
}
