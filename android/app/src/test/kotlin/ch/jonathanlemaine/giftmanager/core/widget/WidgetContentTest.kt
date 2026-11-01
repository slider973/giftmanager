package ch.jonathanlemaine.giftmanager.core.widget

import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetContentTest {
    private val today = LocalDate.of(2026, 12, 1)

    private fun event(date: String, title: String = date, kind: GiftEventKind = GiftEventKind.OTHER) =
        WidgetEvent(kind, title, DayDate.parse(date))

    @Test fun signedOutWins() {
        assertEquals(WidgetModel.SignedOut, WidgetContent.from(WidgetSnapshot(false, listOf(event("2026-12-25"))), today))
    }

    @Test fun noEventWhenListIsEmpty() {
        assertEquals(WidgetModel.NoEvent, WidgetContent.from(WidgetSnapshot(true), today))
    }

    @Test fun pastEventsAreIgnored() {
        val model = WidgetContent.from(WidgetSnapshot(true, listOf(event("2026-11-30"), event("2026-12-25"))), today)
        assertEquals(WidgetModel.Ready(event("2026-12-25"), 24, emptyList()), model)
    }

    @Test fun onlyPastEventsMeansNoEvent() {
        assertEquals(WidgetModel.NoEvent, WidgetContent.from(WidgetSnapshot(true, listOf(event("2026-11-30"))), today))
    }

    @Test fun nextIsTheNearestAndUpcomingFollow() {
        val a = event("2026-12-25")
        val b = event("2027-01-10")
        val c = event("2027-02-01")
        val model = WidgetContent.from(WidgetSnapshot(true, listOf(b, c, a)), today)
        assertEquals(WidgetModel.Ready(a, 24, listOf(b, c)), model)
    }

    @Test fun daysFollowTheRenderDate() {
        val snapshot = WidgetSnapshot(true, listOf(event("2026-12-25")))
        assertEquals(24, (WidgetContent.from(snapshot, today) as WidgetModel.Ready).days)
        assertEquals(20, (WidgetContent.from(snapshot, today.plusDays(4)) as WidgetModel.Ready).days)
        assertEquals(0, (WidgetContent.from(snapshot, LocalDate.of(2026, 12, 25)) as WidgetModel.Ready).days)
    }

    @Test fun countdownTextMatchesIos() {
        assertEquals("Aujourd'hui !", WidgetContent.countdownText(0))
        assertEquals("Aujourd'hui !", WidgetContent.countdownText(-2))
        assertEquals("Demain", WidgetContent.countdownText(1))
        assertEquals("J-2", WidgetContent.countdownText(2))
        assertEquals("J-24", WidgetContent.countdownText(24))
    }
}
