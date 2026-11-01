package ch.jonathanlemaine.giftmanager.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class WidgetSchedulerTest {
    private fun clock(instant: String, zone: ZoneId = ZoneOffset.UTC) = Clock.fixed(Instant.parse(instant), zone)

    @Test fun threeHoursDuringTheDay() {
        assertEquals(Duration.ofHours(3), WidgetScheduler.delayUntilNext(clock("2026-12-01T10:00:00Z")))
    }

    @Test fun justAfterMidnightWhenItComesFirst() {
        // 22 h : minuit + 5 min est dans 2 h 05, avant les 3 h.
        assertEquals(Duration.ofMinutes(125), WidgetScheduler.delayUntilNext(clock("2026-12-01T22:00:00Z")))
    }

    @Test fun threeHoursWhenMidnightIsFurther() {
        // 21 h : minuit + 5 min est dans 3 h 05, donc 3 h.
        assertEquals(Duration.ofHours(3), WidgetScheduler.delayUntilNext(clock("2026-12-01T21:00:00Z")))
    }

    @Test fun usesLocalMidnight() {
        // 22 h UTC = 23 h à Zurich (UTC+1 en hiver) : minuit local dans 1 h.
        assertEquals(Duration.ofMinutes(65), WidgetScheduler.delayUntilNext(clock("2026-12-01T22:00:00Z", ZoneId.of("Europe/Zurich"))))
    }

    @Test fun justPastMidnightWaitsThreeHours() {
        assertEquals(Duration.ofHours(3), WidgetScheduler.delayUntilNext(clock("2026-12-02T00:01:00Z")))
    }
}
