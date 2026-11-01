package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.net.FetchedPage
import ch.jonathanlemaine.giftmanager.net.LinkFetcher
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpLinkPreviewServiceTest {
    private val page = """<html><head><meta property="og:title" content="Casque"></head></html>"""

    private class FakeFetcher(var result: () -> FetchedPage?) : LinkFetcher {
        val urls = mutableListOf<String>()
        override suspend fun fetch(url: String): FetchedPage? {
            urls += url
            return result()
        }
    }

    @Test fun parsesFetchedPage() = runTest {
        val fetcher = FakeFetcher { FetchedPage(page, "https://www.galaxus.ch/p") }
        val preview = HttpLinkPreviewService(fetcher).preview("Regarde https://www.galaxus.ch/p")
        assertEquals("Casque", preview?.title)
        assertEquals(listOf("https://www.galaxus.ch/p"), fetcher.urls)
    }

    @Test fun noLinkMeansNoPreviewAndNoFetch() = runTest {
        val fetcher = FakeFetcher { null }
        assertNull(HttpLinkPreviewService(fetcher).preview("pas de lien"))
        assertEquals(emptyList<String>(), fetcher.urls)
    }

    @Test fun fetchFailureGivesNull() = runTest {
        assertNull(HttpLinkPreviewService(FakeFetcher { null }).preview("https://exemple.ch/a"))
    }

    @Test fun fetcherExceptionIsSwallowed() = runTest {
        val fetcher = FakeFetcher { error("boom") }
        assertNull(HttpLinkPreviewService(fetcher).preview("https://exemple.ch/a"))
    }

    @Test fun fallbackCompletesMissingPrice() = runTest {
        val fetcher = FakeFetcher { FetchedPage(page, "https://www.galaxus.ch/p") }
        val fallback = PreviewSource { _, store ->
            LinkPreview(title = "Autre", price = BigDecimal("99"), currency = store?.currency)
        }
        val preview = HttpLinkPreviewService(fetcher, fallback).preview("https://www.galaxus.ch/p")
        assertEquals("Casque", preview?.title)
        assertEquals(BigDecimal("99"), preview?.price)
        assertEquals("CHF", preview?.currency)
    }

    @Test fun fallbackFailureKeepsPrimary() = runTest {
        val fetcher = FakeFetcher { FetchedPage(page, "https://www.galaxus.ch/p") }
        val fallback = PreviewSource { _, _ -> error("boom") }
        assertEquals("Casque", HttpLinkPreviewService(fetcher, fallback).preview("https://www.galaxus.ch/p")?.title)
    }

    @Test fun fallbackNotUsedWhenPriceKnown() = runTest {
        val priced = """<meta property="og:title" content="X"><meta property="product:price:amount" content="10">"""
        val fetcher = FakeFetcher { FetchedPage("<html><head>$priced</head></html>", "https://www.galaxus.ch/p") }
        var called = false
        val fallback = PreviewSource { _, _ -> called = true; null }
        HttpLinkPreviewService(fetcher, fallback).preview("https://www.galaxus.ch/p")
        assertEquals(false, called)
    }
}
