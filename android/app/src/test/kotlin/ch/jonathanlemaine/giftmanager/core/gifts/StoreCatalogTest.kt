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

    // region Recherche par pays (#78)

    @Test fun swissStoresComeFirstForSwitzerland() {
        val stores = StoreCatalog.searchableStores("CH")
        assertEquals(listOf("franz-carl-weber.ch", "galaxus.ch", "fnac.ch", "manor.ch", "digitec.ch"), stores.map { it.domain })
        assertEquals(setOf("CH"), stores.map { it.country }.toSet())
    }

    @Test fun frenchStoresForFrance() {
        val stores = StoreCatalog.searchableStores("FR")
        assertEquals("king-jouet.com", stores.first().domain)
        assertEquals(setOf("FR"), stores.map { it.country }.toSet())
    }

    @Test fun countryWithoutListFallsBackOnItsAmazon() {
        val stores = StoreCatalog.searchableStores("IT")
        assertEquals(listOf(StoreCatalog.SearchableStore("amazon.it", "Amazon", "IT", "https://www.amazon.it/s?k={q}")), stores)
    }

    @Test fun unknownOrMissingCountryHasNoStore() {
        assertEquals(emptyList<StoreCatalog.SearchableStore>(), StoreCatalog.searchableStores("ZZ"))
        assertEquals(emptyList<StoreCatalog.SearchableStore>(), StoreCatalog.searchableStores(null))
        assertEquals(emptyList<StoreCatalog.SearchableStore>(), StoreCatalog.searchableStores(""))
    }

    @Test fun searchUrlEscapesSpacesAsPercent20() {
        val galaxus = StoreCatalog.searchableStores("CH").first { it.domain == "galaxus.ch" }
        assertEquals("https://www.galaxus.ch/fr/search?q=trottinette%20rouge", StoreCatalog.searchUrl("trottinette rouge", galaxus))
    }

    @Test fun searchUrlEscapesAccentsAndSymbolsInUtf8() {
        val fnac = StoreCatalog.searchableStores("FR").first { it.domain == "fnac.com" }
        assertEquals(
            "https://www.fnac.com/SearchResult/ResultList.aspx?Search=ch%C3%A2teau%20%26%20d%C3%A9s",
            StoreCatalog.searchUrl("château & dés", fnac),
        )
        // Les caractères non réservés d'iOS restent tels quels.
        assertEquals(
            "https://www.fnac.com/SearchResult/ResultList.aspx?Search=a-b_c.d",
            StoreCatalog.searchUrl("a-b_c.d", fnac),
        )
    }

    @Test fun emptyQueryGivesNoUrl() {
        val store = StoreCatalog.searchableStores("CH").first()
        assertNull(StoreCatalog.searchUrl("", store))
        assertNull(StoreCatalog.searchUrl("  \n ", store))
    }

    // endregion
}
