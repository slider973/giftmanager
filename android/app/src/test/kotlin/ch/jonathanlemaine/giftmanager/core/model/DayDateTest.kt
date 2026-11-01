package ch.jonathanlemaine.giftmanager.core.model

import ch.jonathanlemaine.giftmanager.core.Fixtures
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayDateTest {
    private fun day(y: Int, m: Int, d: Int) = DayDate(LocalDate.of(y, m, d))

    private fun child(birth: DayDate?) = Child(UUID.randomUUID(), UUID.randomUUID(), "Emma", birth)

    private fun event(date: DayDate) = GiftEvent(UUID.randomUUID(), null, GiftEventKind.OTHER, "Fête", date)

    @Test fun ageCountsFullYears() {
        val emma = child(day(2018, 3, 12))
        assertEquals(8, emma.age(Fixtures.clock))
        assertEquals(7, emma.age(on = LocalDate.of(2026, 3, 11)))
        assertEquals(8, emma.age(on = LocalDate.of(2026, 3, 12)))
        assertNull(child(null).age(Fixtures.clock))
    }

    @Test fun daysRemainingIsNegativeOncePast() {
        assertEquals(71, event(day(2026, 12, 25)).daysRemaining(Fixtures.clock))
        assertEquals(0, event(day(2026, 10, 15)).daysRemaining(Fixtures.clock))
        assertEquals(-1, event(day(2026, 10, 14)).daysRemaining(Fixtures.clock))
    }

    @Test fun isPastIsFalseOnTheDay() {
        assertFalse(event(day(2026, 10, 15)).isPast(Fixtures.clock))
        assertTrue(event(day(2026, 10, 14)).isPast(Fixtures.clock))
    }

    @Test fun dayDatesAreComparable() {
        assertTrue(day(2026, 1, 1) < day(2026, 1, 2))
        assertEquals("2026-01-02", day(2026, 1, 2).toString())
    }
}
