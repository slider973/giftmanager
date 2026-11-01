package ch.jonathanlemaine.giftmanager.data

import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExchangeRateSourceTest {
    private lateinit var server: MockWebServer
    private val now = Instant.parse("2026-10-02T08:00:00Z")

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun json(rates: String = """{"CHF":0.9279,"GBP":0.85033}""", base: String = "EUR") =
        """{"amount":1.0,"base":"$base","date":"2026-10-02","rates":$rates}"""

    @Test fun payloadParsingKeepsTheShortestDecimalRepresentation() {
        val rates = FrankfurterRateSource.parse(json(), now)
        assertEquals("EUR", rates.base)
        assertEquals("2026-10-02", rates.date)
        assertEquals(BigDecimal("0.9279"), rates.rates["CHF"])
        assertEquals(BigDecimal("0.85033"), rates.rates["GBP"])
        assertEquals("2 octobre", rates.publicationText)
        assertEquals(now, rates.fetchedAt)
    }

    @Test fun codesAreUppercasedAndBadRatesIgnored() {
        val rates = FrankfurterRateSource.parse(json("""{"chf":0.93,"usd":0,"gbp":-1,"jpy":"x"}""", base = "eur"), now)
        assertEquals("EUR", rates.base)
        assertEquals(setOf("CHF"), rates.rates.keys)
    }

    @Test fun emptyOrMalformedTablesAreRejected() {
        assertThrows(SerializationException::class.java) { FrankfurterRateSource.parse(json("{}"), now) }
        assertThrows(SerializationException::class.java) { FrankfurterRateSource.parse("""{"base":"EUR","date":"2026-10-02"}""", now) }
        assertThrows(SerializationException::class.java) { FrankfurterRateSource.parse("""{"date":"2026-10-02","rates":{"CHF":1}}""", now) }
        assertThrows(SerializationException::class.java) { FrankfurterRateSource.parse("pas du json", now) }
    }

    @Test fun defaultEndpointIsFrankfurterEcbRates() {
        assertEquals("https://api.frankfurter.dev/v1/latest?base=EUR", FrankfurterRateSource.DEFAULT_URL)
        assertEquals(10L, FrankfurterRateSource.TIMEOUT_SECONDS)
    }

    @Test fun fetchesAndParsesTheLatestRates() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(json()).build())
        val rates = FrankfurterRateSource(OkHttpClient(), server.url("/v1/latest?base=EUR").toString()).latest()
        assertEquals(BigDecimal("0.9279"), rates.rates["CHF"])
        val request = server.takeRequest()
        assertEquals("/v1/latest", request.url.encodedPath)
        assertEquals("base=EUR", request.url.query)
    }

    @Test fun serverErrorFails() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        val failure = runCatching { FrankfurterRateSource(OkHttpClient(), server.url("/").toString()).latest() }.exceptionOrNull()
        assertTrue(failure.toString(), failure is IOException)
    }
}
