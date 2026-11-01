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

    // MARK: - Prix et devise affichés

    @Test fun displayedPriceTakesFirstAmount() {
        assertEquals(dec("157.09"), LinkPreviewParser.priceFromDisplayedText("157,09 \u20AC avec 13 % d'\u00E9conomies")?.stripTrailingZeros())
        assertEquals(dec("1299.9"), LinkPreviewParser.priceFromDisplayedText("CHF 1'299.90")?.stripTrailingZeros())
        assertEquals(dec("49"), LinkPreviewParser.priceFromDisplayedText("CHF 49.\u2013")?.stripTrailingZeros())
        assertNull(LinkPreviewParser.priceFromDisplayedText("Indisponible"))
    }

    @Test fun displayedCurrencyFromTextThenSymbol() {
        assertEquals("CHF", LinkPreviewParser.currency("149,31 CHF", null))
        assertEquals("EUR", LinkPreviewParser.currency("159,99 \u20AC", null))
        assertEquals("GBP", LinkPreviewParser.currency("\u00A320", null))
        assertEquals("CAD", LinkPreviewParser.currency("CA$ 30", null))
        assertEquals("CHF", LinkPreviewParser.currency("Fr. 20.-", null))
        assertNull(LinkPreviewParser.currency("20", "FR"))
    }

    @Test fun dollarSignFollowsStoreCountry() {
        assertEquals("CAD", LinkPreviewParser.currency("$30", "CA"))
        assertEquals("USD", LinkPreviewParser.currency("$30", "US"))
        assertEquals("USD", LinkPreviewParser.currency("$30", null))
    }

    @Test fun cleanTitleStripsStoreSuffixes() {
        assertEquals("Lego Star Wars", LinkPreviewParser.cleanTitle("Lego Star Wars | Galaxus"))
        assertEquals("Lego Star Wars", LinkPreviewParser.cleanTitle("Lego Star Wars - acheter sur Galaxus"))
        assertEquals("Casque", LinkPreviewParser.cleanTitle("Casque : Amazon.fr: Jeux et Jouets"))
        assertEquals("Livre", LinkPreviewParser.cleanTitle("Livre \u2013 Auteur"))
        assertEquals("Casque Bose", LinkPreviewParser.cleanTitle("Amazon.fr: Casque Bose"))
        assertEquals(200, LinkPreviewParser.cleanTitle("a".repeat(300)).length)
    }

    // MARK: - Pages produit

    private val chf = StoreCatalog.Store("Galaxus", "CH", "CHF")
    private val base = "https://www.galaxus.ch/fr/s1/product/123"

    private fun page(head: String = "", body: String = "") =
        "<html><head>$head</head><body>$body</body></html>"

    private fun ld(json: String) = """<script type="application/ld+json">$json</script>"""

    private fun parse(html: String, store: StoreCatalog.Store? = chf) = LinkPreviewParser.parse(html, base, store)

    @Test fun jsonLdProductGivesTitlePriceCurrencyImage() {
        val preview = parse(
            page(ld("""{"@type":"Product","name":"Lego Technic","image":"https://img.example/a.jpg",
                "offers":{"@type":"Offer","price":"199.90","priceCurrency":"eur"}}""")),
        )!!
        assertEquals("Lego Technic", preview.title)
        assertEquals("https://img.example/a.jpg", preview.imageUrl)
        assertEquals(dec("199.9"), preview.price?.stripTrailingZeros())
        assertEquals("EUR", preview.currency)
    }

    @Test fun jsonLdInGraphAndTypeArray() {
        val preview = parse(
            page(ld("""{"@graph":[{"@type":"WebSite","name":"Boutique"},
                {"@type":["Thing","Product"],"name":"Velo","offers":{"price":450,"priceCurrency":"CHF"}}]}""")),
        )!!
        assertEquals("Velo", preview.title)
        assertEquals(dec("450"), preview.price?.stripTrailingZeros())
    }

    @Test fun jsonLdOffersArrayUsesFirst() {
        val preview = parse(
            page(ld("""[{"@type":"Product","name":"X","offers":[{"price":"10.00","priceCurrency":"CHF"},{"price":"99"}]}]""")),
        )!!
        assertEquals(dec("10"), preview.price?.stripTrailingZeros())
    }

    @Test fun jsonLdAggregateOfferUsesLowPrice() {
        val preview = parse(
            page(ld("""{"@type":"Product","name":"X","offers":{"@type":"AggregateOffer","lowPrice":"25.5","highPrice":"40","priceCurrency":"CHF"}}""")),
        )!!
        assertEquals(dec("25.5"), preview.price?.stripTrailingZeros())
    }

    @Test fun jsonLdPriceSpecification() {
        val preview = parse(
            page(ld("""{"@type":"Product","name":"X","offers":{"priceSpecification":[{"price":"30","priceCurrency":"GBP"}]}}""")),
        )!!
        assertEquals(dec("30"), preview.price?.stripTrailingZeros())
        assertEquals("GBP", preview.currency)
    }

    @Test fun jsonLdImageArrayAndObject() {
        val array = parse(page(ld("""{"@type":"Product","name":"X","image":["https://a/1.jpg","https://a/2.jpg"]}""")))!!
        assertEquals("https://a/1.jpg", array.imageUrl)
        val objects = parse(page(ld("""{"@type":"Product","name":"X","image":[{"url":"https://a/3.jpg"}]}""")))!!
        assertEquals("https://a/3.jpg", objects.imageUrl)
        val single = parse(page(ld("""{"@type":"Product","name":"X","image":{"url":"https://a/4.jpg"}}""")))!!
        assertEquals("https://a/4.jpg", single.imageUrl)
    }

    @Test fun jsonLdWithoutCurrencyFallsBackToStore() {
        val preview = parse(page(ld("""{"@type":"Product","name":"X","offers":{"price":"12"}}""")))!!
        assertEquals("CHF", preview.currency)
        assertNull(parse(page(ld("""{"@type":"Product","name":"X","offers":{"price":"12"}}"""), ), null)!!.currency)
    }

    @Test fun invalidJsonLdIsIgnored() {
        val preview = parse(page(head = "<title>Page</title>" + ld("{pas du json")))!!
        assertEquals("Page", preview.title)
        assertNull(preview.price)
    }

    @Test fun openGraphAndProductMeta() {
        val preview = parse(
            page(
                """<meta property="og:title" content="Casque | Galaxus">
                <meta property="og:image" content="https://img.example/c.jpg">
                <meta property="product:price:amount" content="1'299.90">
                <meta property="product:price:currency" content="chf">""",
            ),
        )!!
        assertEquals("Casque", preview.title)
        assertEquals("https://img.example/c.jpg", preview.imageUrl)
        assertEquals(dec("1299.9"), preview.price?.stripTrailingZeros())
        assertEquals("CHF", preview.currency)
    }

    @Test fun ogPriceAmountIsUsed() {
        val preview = parse(page("""<meta property="og:title" content="X"><meta property="og:price:amount" content="199,99">"""), null)!!
        assertEquals(dec("199.99"), preview.price?.stripTrailingZeros())
        assertNull(preview.currency)
    }

    @Test fun itempropPriceWithCurrency() {
        val preview = parse(
            page(
                "<title>Page</title>",
                """<span itemprop="price" content="49.50"></span><meta itemprop="priceCurrency" content="EUR">""",
            ),
        )!!
        assertEquals(dec("49.5"), preview.price?.stripTrailingZeros())
        assertEquals("EUR", preview.currency)
    }

    @Test fun itempropPriceFromText() {
        val preview = parse(page("<title>Page</title>", """<span itemprop="price">CHF 49.&ndash;</span>"""))!!
        assertEquals(dec("49"), preview.price?.stripTrailingZeros())
    }

    @Test fun amazonDisplayedPriceUsesTextCurrency() {
        val amazon = StoreCatalog.Store("Amazon", "FR", "EUR")
        val preview = LinkPreviewParser.parse(
            page(
                """<meta property="og:title" content="Amazon.fr: Casque Bose">""",
                """<div id="corePrice_feature_div"><span class="a-offscreen">157,09 CHF avec 13 % d'\u00E9conomies</span></div>""",
            ),
            "https://www.amazon.fr/dp/X",
            amazon,
        )!!
        assertEquals("Casque Bose", preview.title)
        assertEquals(dec("157.09"), preview.price?.stripTrailingZeros())
        assertEquals("CHF", preview.currency)
    }

    @Test fun displayedPriceWithoutSymbolUsesStoreCurrency() {
        val amazon = StoreCatalog.Store("Amazon", "FR", "EUR")
        val preview = LinkPreviewParser.parse(
            page("<title>X</title>", """<span class="a-price"><span class="a-offscreen">199,99</span></span>"""),
            "https://www.amazon.fr/dp/X",
            amazon,
        )!!
        assertEquals(dec("199.99"), preview.price?.stripTrailingZeros())
        assertEquals("EUR", preview.currency)
    }

    @Test fun dollarOnCanadianStoreIsCad() {
        val store = StoreCatalog.Store("Boutique", "CA", "CAD")
        val preview = LinkPreviewParser.parse(
            page("<title>X</title>", """<span class="a-price"><span class="a-offscreen">$30.00</span></span>"""),
            "https://shop.example.ca/p",
            StoreCatalog.Store("Boutique", "CA", null),
        )!!
        assertEquals("CAD", preview.currency)
        assertEquals("CAD", store.currency)
    }

    @Test fun imageFallbacks() {
        assertEquals("https://i/s.jpg", parse(page("""<meta property="og:image:secure_url" content="https://i/s.jpg">"""))?.imageUrl)
        assertEquals("https://i/t.jpg", parse(page("""<meta name="twitter:image" content="https://i/t.jpg">"""))?.imageUrl)
        assertEquals("https://i/l.jpg", parse(page("", """<img id="landingImage" src="https://i/l.jpg">"""))?.imageUrl)
        assertEquals(
            "https://i/hi.jpg",
            parse(page("", """<img id="landingImage" data-old-hires="https://i/hi.jpg" src="https://i/l.jpg">"""))?.imageUrl,
        )
    }

    @Test fun relativeImageIsResolved() {
        assertEquals(
            "https://www.galaxus.ch/img/a.jpg",
            parse(page("""<meta property="og:image" content="/img/a.jpg">"""))?.imageUrl,
        )
    }

    @Test fun titleOrderIsOgThenJsonLdThenTag() {
        val withOg = parse(page("""<title>Tag</title><meta property="og:title" content="Og">""" + ld("""{"@type":"Product","name":"Ld"}""")))
        assertEquals("Og", withOg?.title)
        val withLd = parse(page("<title>Tag</title>" + ld("""{"@type":"Product","name":"Ld"}""")))
        assertEquals("Ld", withLd?.title)
        assertEquals("Tag", parse(page("<title>Tag</title>"))?.title)
    }

    @Test fun emptyPageHasNoPreview() {
        assertNull(parse(page()))
        assertNull(parse(""))
    }

    @Test fun unreadablePriceIsDropped() {
        val preview = parse(page("""<meta property="og:title" content="X"><meta property="product:price:amount" content="gratuit">"""))!!
        assertNull(preview.price)
        assertNull(preview.currency)
    }
}
