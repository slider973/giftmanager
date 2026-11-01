package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import java.math.BigDecimal
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `StoreCatalogTests.swift` (boutiques et tri des liens). */
class StoreCatalogTest {
    @Test fun knownStoresGiveCountryAndCurrency() {
        assertEquals(StoreCatalog.Store("Galaxus", "CH", "CHF"), StoreCatalog.store("https://www.galaxus.ch/fr/s1/product/123"))
        assertEquals("FR", StoreCatalog.store("https://www.amazon.fr/dp/B09QFZ2X7C")?.country)
        assertEquals("EUR", StoreCatalog.store("https://amazon.de/dp/X")?.currency)
    }

    @Test fun genericDomainsHaveNoCountry() {
        assertEquals(StoreCatalog.Store("LEGO", null, null), StoreCatalog.store("https://www.lego.com/fr-ch/product/1"))
    }

    @Test fun unknownStoreUsesDomainAndTopLevelDomain() {
        val store = StoreCatalog.store("https://www.jouets-du-lac.ch/produit/42")
        assertEquals("Jouets-du-lac", store?.name)
        assertEquals("CH", store?.country)
        assertEquals("CHF", store?.currency)
    }

    @Test fun subdomainsFallBackOnTheLastTwoLevels() {
        assertEquals("Galaxus", StoreCatalog.store("https://shop.fr.galaxus.ch/x")?.name)
    }

    @Test fun unknownExtensionHasNoCountry() {
        val store = StoreCatalog.store("https://boutique.xyz/a")
        assertEquals("Boutique", store?.name)
        assertNull(store?.country)
        assertNull(store?.currency)
    }

    @Test fun invalidUrlHasNoStore() {
        assertNull(StoreCatalog.store("pas une url"))
    }

    @Test fun currencyByCountry() {
        assertEquals("CHF", StoreCatalog.currency("LI"))
        assertEquals("GBP", StoreCatalog.currency("GB"))
        assertNull(StoreCatalog.currency("JP"))
        assertNull(StoreCatalog.currency(null))
    }

    private fun link(store: String, country: String, price: Int, currency: String) =
        ItemLink(UUID.randomUUID(), UUID(0, 1), "https://$store", store, country, BigDecimal(price), currency)

    @Test fun linksOfReaderCountryComeFirst() {
        val fr = link("Amazon", "FR", 10, "EUR")
        val ch = link("Galaxus", "CH", 11, "CHF")
        assertEquals("CH", StoreCatalog.sorted(listOf(fr, ch), "CH").first().country)
        assertEquals("FR", StoreCatalog.sorted(listOf(ch, fr), "FR").first().country)
    }

    @Test fun otherLinksAreSortedByStore() {
        val a = link("Zalando", "FR", 1, "EUR")
        val b = link("Amazon", "FR", 1, "EUR")
        assertEquals(listOf("Amazon", "Zalando"), StoreCatalog.sorted(listOf(a, b), "CH").map { it.store })
    }
}
