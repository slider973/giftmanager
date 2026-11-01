package ch.jonathanlemaine.giftmanager.core.currency

import ch.jonathanlemaine.giftmanager.data.ExchangeRateSource
import ch.jonathanlemaine.giftmanager.data.prefs.ExchangeRateCache
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

private class FakeSource(var result: () -> ExchangeRates = { sampleRates() }) : ExchangeRateSource {
    var calls = 0
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun latest(): ExchangeRates {
        calls++
        gate?.await()
        return result()
    }
}

private class MemoryCache(var stored: ExchangeRates? = null) : ExchangeRateCache {
    var loads = 0
    var failSave = false

    override suspend fun load(): ExchangeRates? {
        loads++
        return stored
    }

    override suspend fun save(rates: ExchangeRates) {
        if (failSave) throw IOException("disque plein")
        stored = rates
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyServiceTest {
    private val start = Instant.parse("2026-10-02T10:00:00Z")
    private val clock = MutableClock(start)

    private fun TestScope.service(source: FakeSource, cache: MemoryCache = MemoryCache()) =
        CurrencyService(source, cache, clock, backgroundScope)

    @Test fun refreshStoresRatesInTheCacheAndStampsThem() = runTest {
        val cache = MemoryCache()
        val service = service(FakeSource { sampleRates(date = "2026-10-02", fetchedAt = Instant.EPOCH) }, cache)
        assertTrue(service.isStale)
        service.refresh()
        assertEquals("2026-10-02", service.rates.value?.date)
        assertEquals("2026-10-02", cache.stored?.date)
        // fetchedAt est l'heure de l'appareil, pas celle du serveur.
        assertEquals(start, service.rates.value?.fetchedAt)
        assertFalse(service.isStale)
    }

    @Test fun cacheServesTheLastKnownRatesAndIsLoadedOnce() = runTest {
        val cache = MemoryCache(sampleRates(date = "2026-10-01", fetchedAt = start))
        val source = FakeSource { sampleRates(date = "2099-01-01") }
        val service = service(source, cache)
        service.refreshIfNeeded()
        service.refreshIfNeeded()
        assertEquals("2026-10-01", service.rates.value?.date)
        assertEquals(1, cache.loads)
        assertEquals(0, source.calls)
    }

    @Test fun staleRatesAreRefreshedAfterSixHours() = runTest {
        val cache = MemoryCache(sampleRates(date = "2026-10-01", fetchedAt = start))
        val source = FakeSource { sampleRates(date = "2026-10-02") }
        val service = service(source, cache)
        clock.now = start.plus(Duration.ofHours(6))
        service.refreshIfNeeded()
        assertEquals("exactement 6 h : encore valable", 0, source.calls)
        clock.now = start.plus(Duration.ofHours(6)).plusSeconds(1)
        service.refreshIfNeeded()
        assertEquals(1, source.calls)
        assertEquals("2026-10-02", service.rates.value?.date)
    }

    @Test fun offlineKeepsTheLastKnownRates() = runTest {
        val cache = MemoryCache(sampleRates(date = "2026-09-30", fetchedAt = start))
        val source = FakeSource { throw IOException("hors ligne") }
        val service = service(source, cache)
        clock.now = start.plus(Duration.ofDays(3))
        assertNull(service.rates.value)
        service.refreshIfNeeded()
        assertEquals(1, source.calls)
        assertTrue(service.isStale)
        assertEquals("2026-09-30", service.rates.value?.date)
        assertEquals("≈ 100 €", RatesApproximator(service.rates.value).approxText(dec("93"), "CHF", "EUR").plain())
    }

    @Test fun failureWithoutCacheIsSilent() = runTest {
        val service = service(FakeSource { throw IOException("hors ligne") })
        service.refresh()
        assertNull(service.rates.value)
        assertTrue(service.isStale)
    }

    @Test fun unwritableCacheDoesNotLoseTheFreshRates() = runTest {
        val service = service(FakeSource(), MemoryCache().apply { failSave = true })
        service.refresh()
        assertEquals("2026-10-01", service.rates.value?.date)
    }

    @Test fun concurrentRefreshesShareOneRequestAndSurviveACancelledCaller() = runTest {
        val source = FakeSource().apply { gate = CompletableDeferred() }
        val service = service(source)
        val first = backgroundScope.launch { service.refresh() }
        runCurrent()
        val second = backgroundScope.launch { service.refresh() }
        runCurrent()
        assertEquals(1, source.calls)
        // L'écran qui a lancé le premier appel disparaît : la requête continue pour les autres.
        first.cancel()
        runCurrent()
        source.gate!!.complete(Unit)
        runCurrent()
        assertTrue(second.isCompleted)
        assertEquals("2026-10-01", service.rates.value?.date)
        assertEquals(1, source.calls)
        // La requête terminée, un nouveau rafraîchissement repart.
        service.refresh()
        assertEquals(2, source.calls)
    }
}
