package ch.jonathanlemaine.giftmanager.core.gifts

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Normalisation des liens saisis ou partagés, et lecture des prix (`StoreCatalogTests.swift`). */
class LinkPreviewParserTest {
    private fun price(text: String) = LinkPreviewParser.parsePrice(text)?.stripTrailingZeros()

    private fun dec(text: String) = BigDecimal(text).stripTrailingZeros()

    @Test fun sharedTextExtractsFirstLink() {
        assertEquals(
            "https://www.galaxus.ch/fr/s1/product/123",
            LinkPreviewParser.normalizedUrl("Regarde ça 👉 https://www.galaxus.ch/fr/s1/product/123 trop bien"),
        )
    }

    @Test fun missingSchemeGetsHttps() {
        assertEquals("https://amazon.fr/dp/X", LinkPreviewParser.normalizedUrl("amazon.fr/dp/X"))
    }

    @Test fun existingSchemeIsKept() {
        assertEquals("http://exemple.ch/a", LinkPreviewParser.normalizedUrl("  http://exemple.ch/a  "))
    }

    @Test fun trailingPunctuationIsDropped() {
        assertEquals("https://www.fnac.ch/a1", LinkPreviewParser.normalizedUrl("Voir https://www.fnac.ch/a1."))
    }

    @Test fun hostWithoutDotIsRejected() {
        assertNull(LinkPreviewParser.normalizedUrl("localhost"))
        assertNull(LinkPreviewParser.normalizedUrl("https://intranet/a"))
    }

    @Test fun textWithoutLinkIsRejected() {
        assertNull(LinkPreviewParser.normalizedUrl("un vélo rouge"))
        assertNull(LinkPreviewParser.normalizedUrl("   "))
        assertNull(LinkPreviewParser.normalizedUrl(""))
    }

    @Test fun priceParsing() {
        assertEquals(dec("199.99"), price("199,99"))
        assertEquals(dec("1299.00"), price("1'299.00"))
        assertEquals(dec("49"), price("CHF 49.–"))
        assertEquals(dec("1299.00"), price("1.299,00"))
        assertEquals(dec("1299.90"), price("1'299.90"))
        assertEquals(dec("19.99"), price("19,99"))
        assertEquals(dec("1299"), price("1.299"))
    }

    @Test fun unreadablePriceIsNull() {
        assertNull(LinkPreviewParser.parsePrice("999999999"))
        assertNull(LinkPreviewParser.parsePrice("gratuit"))
        assertNull(LinkPreviewParser.parsePrice(""))
    }
}
