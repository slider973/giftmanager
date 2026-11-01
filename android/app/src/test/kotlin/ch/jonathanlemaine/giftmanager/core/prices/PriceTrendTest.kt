package ch.jonathanlemaine.giftmanager.core.prices

import ch.jonathanlemaine.giftmanager.core.model.PriceCheck
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port de `PriceTrendTests.swift`. */
class PriceTrendTest {
    private val base = Instant.ofEpochSecond(1_800_000_000)

    private fun link(n: Int) = UUID(0, n.toLong())

    private fun check(
        link: UUID, daysAgo: Long, price: String?, currency: String? = "CHF", inStock: Boolean? = true,
    ) = PriceCheck(
        link, "https://www.galaxus.ch/p/1", price?.let(::BigDecimal), currency, inStock, base.minusSeconds(daysAgo * 86_400),
    )

    private fun PriceTrend.prices() = points.map { it.price.toInt() }

    @Test fun trendFromChecksInAnyOrder() {
        val l = link(1)
        val trends = PriceTrend.trends(listOf(check(l, 0, "169"), check(l, 6, "199"), check(l, 3, "189")))
        assertEquals(1, trends.size)
        assertEquals(listOf(199, 189, 169), trends[0].prices())
        assertEquals(0, BigDecimal(-30).compareTo(trends[0].change))
        assertEquals(PriceTrend.Direction.DOWN, trends[0].direction)
        assertEquals("Galaxus", trends[0].storeName)
    }

    @Test fun missingPricesAndOtherCurrenciesAreSkipped() {
        val l = link(1)
        val trend = PriceTrend.trends(
            listOf(check(l, 4, "180", currency = "EUR"), check(l, 3, "199"), check(l, 2, null), check(l, 1, "205")),
        )[0]
        assertEquals(listOf(199, 205), trend.prices())
        assertEquals(PriceTrend.Direction.UP, trend.direction)
        assertEquals("CHF", trend.currency)
    }

    @Test fun stockComesFromLatestCheckThatKnowsIt() {
        val l = link(1)
        val trend = PriceTrend.trends(listOf(check(l, 2, "199", inStock = false), check(l, 1, "199", inStock = null)))[0]
        assertEquals(false, trend.inStock)
        assertEquals(PriceTrend.Direction.FLAT, trend.direction)
    }

    @Test fun outOfStockAndDropsComeFirst() {
        val stable = link(1)
        val drop = link(2)
        val gone = link(3)
        val trends = PriceTrend.trends(
            listOf(
                check(stable, 2, "50"), check(stable, 1, "50"),
                check(drop, 2, "50"), check(drop, 1, "40"),
                check(gone, 1, "60", inStock = false),
            ),
        )
        assertEquals(listOf(gone, drop, stable), trends.map { it.linkId })
    }

    @Test fun emptyHistoryShowsNothing() {
        assertTrue(PriceTrend.trends(emptyList()).isEmpty())
        assertTrue(PriceTrend.trends(listOf(check(link(1), 1, null, inStock = null))).isEmpty())
    }

    @Test fun unknownStoreFallsBackToItsDomainAndNoUrlToBoutique() {
        val trend = PriceTrend(link(1), "pas une url", emptyList(), null, true, base)
        assertEquals("Boutique", trend.storeName)
    }
}
