package ch.jonathanlemaine.giftmanager.core.format

import ch.jonathanlemaine.giftmanager.core.model.DayDate
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattingTest {
    /** Espaces insécables (fines ou non) ramenés à une espace simple : le JDK et ICU n'en choisissent pas les mêmes. */
    private fun String?.plain(): String? = this?.replace(' ', ' ')?.replace(' ', ' ')

    @Test fun countdownText() {
        assertEquals("Passé", Formatting.countdownText(-3))
        assertEquals("Aujourd'hui", Formatting.countdownText(0))
        assertEquals("Demain", Formatting.countdownText(1))
        assertEquals("Dans 71 jours", Formatting.countdownText(71))
    }

    @Test fun ageText() {
        assertNull(Formatting.ageText(null))
        assertEquals("0 an", Formatting.ageText(0))
        assertEquals("1 an", Formatting.ageText(1))
        assertEquals("8 ans", Formatting.ageText(8))
    }

    @Test fun dateTextIsFrench() {
        assertEquals("25 décembre 2026", Formatting.dateText(DayDate(LocalDate.of(2026, 12, 25))))
        assertEquals("1 février 2026", Formatting.dateText(DayDate(LocalDate.of(2026, 2, 1))))
    }

    @Test fun moneyFormatsEuroInFrench() {
        assertEquals("12,50 €", Money.format(BigDecimal("12.5"), "EUR").plain())
        assertEquals("1 234,00 €", Money.format(BigDecimal("1234"), "EUR").plain())
    }

    @Test fun moneyDefaultsToEuroAndHandlesNull() {
        assertEquals("5,00 €", Money.format(BigDecimal("5"), null).plain())
        assertNull(Money.format(null, "EUR"))
    }

    @Test fun moneyFormatsFrancsWithSwissLocale() {
        val text = Money.format(BigDecimal("1234.5"), "CHF").plain()!!
        assertTrue(text, text.contains("CHF"))
        assertTrue(text, text.contains("1") && text.contains("234") && text.contains("50"))
    }

    @Test fun moneyWithUnknownCurrencyDoesNotThrow() {
        assertEquals("3.5 ZZZZ", Money.format(BigDecimal("3.5"), "ZZZZ"))
    }

    @Test fun countries() {
        assertEquals("Suisse", Countries.name("CH"))
        assertEquals("XX", Countries.name("XX"))
        assertEquals("—", Countries.name(null))
        assertEquals("CH", Countries.all.first().code)
        assertEquals("🇨🇭", Countries.all.first().flag)
        assertEquals(listOf("CHF", "EUR", "GBP", "USD", "CAD"), Countries.currencies)
    }

    @Test fun avatarPalette() {
        assertEquals(5, AvatarPalette.colors.size)
        assertEquals(12, AvatarPalette.emojis.size)
    }
}
