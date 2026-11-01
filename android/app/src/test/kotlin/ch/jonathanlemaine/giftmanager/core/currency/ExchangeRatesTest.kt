package ch.jonathanlemaine.giftmanager.core.currency

import ch.jonathanlemaine.giftmanager.core.model.GiftJson
import java.math.BigDecimal
import java.time.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les formats français utilisent des espaces insécables : on les normalise pour comparer. */
internal fun String?.plain(): String? = this?.replace(' ', ' ')?.replace(' ', ' ')

internal fun dec(text: String) = BigDecimal(text)

/** 1 EUR = 0,93 CHF = 0,85 GBP (base BCE). */
internal fun sampleRates(date: String = "2026-10-01", fetchedAt: Instant = Instant.parse("2026-10-01T10:00:00Z")) =
    ExchangeRates("EUR", date, mapOf("CHF" to dec("0.93"), "GBP" to dec("0.85")), fetchedAt)

private fun assertSame(expected: String, actual: BigDecimal?) =
    assertEquals("attendu $expected, obtenu $actual", 0, dec(expected).compareTo(actual))

class ExchangeRatesTest {
    // region Conversion

    @Test fun convertsThroughBaseCurrency() {
        val rates = sampleRates()
        assertSame("93", rates.convert(dec("100"), "EUR", "CHF"))
        assertSame("100", rates.convert(dec("93"), "CHF", "EUR"))
        // CHF -> GBP : 93 CHF = 100 EUR = 85 GBP.
        assertSame("85", rates.convert(dec("93"), "chf", "GBP"))
    }

    @Test fun sameCurrencyIsUnchanged() {
        assertSame("199.99", sampleRates().convert(dec("199.99"), "CHF", "CHF"))
    }

    @Test fun missingRateGivesNoConversion() {
        assertNull(sampleRates().convert(dec("100"), "EUR", "JPY"))
        assertNull(sampleRates().convert(dec("100"), "XYZ", "EUR"))
    }

    @Test fun nonPositiveRateIsIgnored() {
        val rates = ExchangeRates("EUR", "2026-10-01", mapOf("CHF" to BigDecimal.ZERO), Instant.EPOCH)
        assertNull(rates.rate("CHF"))
        assertNull(rates.convert(dec("10"), "CHF", "EUR"))
    }

    @Test fun divisionKeepsDecimal128Precision() {
        val rates = ExchangeRates("EUR", "2026-10-01", mapOf("CHF" to dec("0.9279")), Instant.EPOCH)
        val eur = rates.convert(dec("199"), "CHF", "EUR")!!
        // 199 / 0,9279 = 214,4627...
        assertTrue(eur.toPlainString(), eur > dec("214.46") && eur < dec("214.47"))
    }

    // endregion

    // region Arrondis et texte

    @Test fun roundingToUnitsAboveTenAndCentsBelow() {
        assertSame("147", IndicativePrice.rounded(dec("147.49")))
        assertSame("148", IndicativePrice.rounded(dec("147.5")))
        assertSame("4.57", IndicativePrice.rounded(dec("4.567")))
        assertSame("10", IndicativePrice.rounded(dec("9.999")))
    }

    @Test fun indicativeTextIsMarkedApproximate() {
        assertEquals("≈ 147 €", IndicativePrice.text(dec("147.2"), "EUR").plain())
        assertEquals("≈ 4,80 €", IndicativePrice.text(dec("4.8"), "EUR").plain())
        val chf = IndicativePrice.text(dec("214.5"), "CHF").plain()!!
        assertTrue(chf, chf.startsWith("≈ ") && chf.contains("215") && chf.contains("CHF") && !chf.contains(","))
        assertEquals("environ 147 €, conversion indicative", IndicativePrice.spokenText(dec("147"), "EUR").plain())
        assertEquals("147 €", IndicativePrice.amountText(dec("147.2"), "EUR").plain())
    }

    @Test fun unknownCurrencyCodeKeepsTheAmount() {
        assertEquals("12 XYZ", IndicativePrice.amountText(dec("12.4"), "xyz"))
    }

    @Test fun publicationTextIsFrench() {
        assertEquals("2 octobre", sampleRates(date = "2026-10-02").publicationText)
        assertEquals("1 janvier", sampleRates(date = "2026-01-01").publicationText)
        assertNull(sampleRates(date = "pas une date").publicationText)
    }

    // endregion

    @Test fun roundTripsThroughJsonWithoutLosingPrecision() {
        val rates = sampleRates().copy(rates = mapOf("CHF" to dec("0.9279")))
        val decoded = GiftJson.read.decodeFromString<ExchangeRates>(GiftJson.write.encodeToString(rates))
        assertEquals(rates, decoded)
        assertFalse(GiftJson.write.encodeToString(rates).contains("\"0.9279\""))
    }
}
