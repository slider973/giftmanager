package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.session.testChild
import ch.jonathanlemaine.giftmanager.data.itemLink
import ch.jonathanlemaine.giftmanager.data.wishItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLogicTest {
    private val lea = testChild(1, name = "Léa")
    private val zoe = testChild(3, householdN = 2, name = "Zoé")

    private fun list(child: ch.jonathanlemaine.giftmanager.core.model.Child, isParent: Boolean, items: List<WishItem>, links: List<ItemLink> = emptyList()) =
        SearchList(child, isParent, items, links.groupBy { it.itemId })

    private val leaList = list(lea, true, listOf(wishItem(1, title = "Lego City", status = null), wishItem(2, title = "Vélo", status = null)))
    private val zoeList = list(
        zoe, false,
        listOf(
            wishItem(11, childN = 3, title = "Poupée"),
            wishItem(12, childN = 3, title = "Lego Friends", status = ItemStatus.TAKEN),
            wishItem(13, childN = 3, title = "Déjà là", owned = true),
        ),
    )
    private val lists = listOf(leaList, zoeList)

    private fun titles(groups: List<SearchGroup>) = groups.flatMap { g -> g.results.map { it.item.title } }

    @Test fun noFilterShowsEverythingExceptOwnedGiftsGroupedInFamilyOrder() {
        val groups = SearchLogic.groups(lists, SearchFilters())
        assertEquals(listOf("Léa", "Zoé"), groups.map { it.child.firstName })
        assertEquals(listOf("Lego City", "Vélo", "Poupée", "Lego Friends"), titles(groups))
    }

    @Test fun queryIsTrimmedAndCaseInsensitiveOnTheTitle() {
        assertEquals(listOf("Lego City", "Lego Friends"), titles(SearchLogic.groups(lists, SearchFilters(query = "  LEGO "))))
    }

    @Test fun queryAlsoMatchesTheChildsFirstName() {
        val groups = SearchLogic.groups(lists, SearchFilters(query = "zoé"))
        assertEquals(listOf("Zoé"), groups.map { it.child.firstName })
        assertEquals(3 - 1, groups.single().results.size) // le cadeau déjà possédé reste exclu
    }

    @Test fun anEmptyQueryAfterTrimmingMatchesEverything() {
        assertEquals(4, titles(SearchLogic.groups(lists, SearchFilters(query = "   "))).size)
    }

    @Test fun onlyAvailableExcludesParentListsAndAnyOtherStatus() {
        val groups = SearchLogic.groups(lists, SearchFilters(onlyAvailable = true))
        assertEquals(listOf("Zoé"), groups.map { it.child.firstName })
        assertEquals(listOf("Poupée"), titles(groups))
    }

    @Test fun countryFilterKeepsGiftsWithALinkFromThatCountry() {
        val links = listOf(itemLink(1, 11, country = "FR"), itemLink(2, 12, country = "CH"), itemLink(3, 1, country = "FR"))
        val withLinks = listOf(leaList.copy(links = links.groupBy { it.itemId }.filterKeys { it == leaList.items[0].id }), zoeList.copy(links = links.groupBy { it.itemId }))
        assertEquals(listOf("Lego City", "Poupée"), titles(SearchLogic.groups(withLinks, SearchFilters(country = "FR"))))
        assertEquals(listOf("Lego Friends"), titles(SearchLogic.groups(withLinks, SearchFilters(country = "CH"))))
    }

    @Test fun aChildWithoutResultsIsOmitted() {
        assertTrue(SearchLogic.groups(lists, SearchFilters(query = "zzz")).isEmpty())
    }

    @Test fun noBadgeOnAParentsListButABadgeOnOthers() {
        val groups = SearchLogic.groups(lists, SearchFilters())
        assertTrue(groups[0].results.all { it.badge == null })
        assertNotNull(groups[1].results.first { it.item.title == "Lego Friends" }.badge)
    }

    @Test fun bestLinkPrefersTheReadersCountry() {
        val links = listOf(itemLink(1, 11, country = "FR", store = "Amazon"), itemLink(2, 11, country = "CH", store = "Galaxus"))
        val withLinks = listOf(zoeList.copy(links = links.groupBy { it.itemId }))
        val result = SearchLogic.groups(withLinks, SearchFilters(), preferredCountry = "CH").single().results.first()
        assertEquals("Galaxus", result.link?.store)
        assertNull(SearchLogic.groups(listOf(zoeList), SearchFilters()).single().results.first().link)
    }

    @Test fun countriesAreDistinctAndSorted() {
        val links = listOf(itemLink(1, 11, country = "FR"), itemLink(2, 12, country = "CH"), itemLink(3, 11, country = "FR"), itemLink(4, 1, country = null))
        val withLinks = listOf(leaList.copy(links = links.groupBy { it.itemId }), zoeList)
        assertEquals(listOf("CH", "FR"), SearchLogic.countries(withLinks))
        assertEquals(emptyList<String>(), SearchLogic.countries(lists))
    }
}
