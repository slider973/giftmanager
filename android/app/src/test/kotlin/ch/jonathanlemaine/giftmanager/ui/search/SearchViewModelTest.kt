package ch.jonathanlemaine.giftmanager.ui.search

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.session.testChild
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.itemLink
import ch.jonathanlemaine.giftmanager.data.wishItem
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftsEnv
import ch.jonathanlemaine.giftmanager.ui.gifts.giftsEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    // Léa (mon enfant, statut nul) puis Zoé (autre foyer : un cadeau disponible, un pris).
    private suspend fun TestScope.env(setup: GiftsEnv.() -> Unit = {}) = giftsEnv {
        items.items += listOf(
            wishItem(1, childN = 1, title = "Lego City", status = null),
            wishItem(11, childN = 3, title = "Poupée"),
            wishItem(12, childN = 3, title = "Lego Friends", status = ItemStatus.TAKEN),
        )
        items.itemLinks += itemLink(1, 11, country = "FR", store = "Amazon")
        items.itemLinks += itemLink(2, 12, country = "CH")
        setup()
    }

    private fun TestScope.vm(env: GiftsEnv) = SearchViewModel(env.session, env.items, Fixtures.clock).also { runCurrent() }

    private val SearchViewModel.state get() = uiState.value

    @Test fun loadsEveryChildsListInFamilyOrder() = runTest {
        val vm = vm(env())
        assertTrue(vm.state.hasLoaded)
        assertEquals(listOf("Léa", "Zoé"), vm.state.groups.map { it.child.firstName })
        assertEquals(3, vm.state.resultCount)
        assertEquals(listOf("CH", "FR"), vm.state.countries)
    }

    @Test fun listsAreLoadedForTheCurrentEvent() = runTest {
        val env = env()
        vm(env)
        // Noël (25 décembre) est l'événement courant à la date des tests.
        assertEquals(env.christmas.id, env.items.lastEventFilter)
    }

    @Test fun noBadgeOnMyChildsListButOnOthers() = runTest {
        val vm = vm(env())
        val lea = vm.state.groups.first { it.child.firstName == "Léa" }
        val zoe = vm.state.groups.first { it.child.firstName == "Zoé" }
        assertTrue(lea.isParentList)
        assertTrue(lea.results.all { it.badge == null })
        assertFalse(zoe.isParentList)
        assertTrue(zoe.results.all { it.badge != null })
    }

    @Test fun typingFiltersWithoutAnyRequest() = runTest {
        val env = env()
        val vm = vm(env)
        val calls = env.items.childItemsCalls
        val linkCalls = env.items.linksCalls
        vm.onQueryChange("lego")
        assertEquals(2, vm.state.resultCount)
        vm.onQueryChange("zoé")
        assertEquals(listOf("Zoé"), vm.state.groups.map { it.child.firstName })
        assertEquals(calls, env.items.childItemsCalls)
        assertEquals(linkCalls, env.items.linksCalls)
    }

    @Test fun onlyAvailableKeepsOthersAvailableGifts() = runTest {
        val vm = vm(env())
        vm.toggleOnlyAvailable()
        assertTrue(vm.state.filters.onlyAvailable)
        assertEquals(listOf("Poupée"), vm.state.groups.flatMap { g -> g.results.map { it.item.title } })
        vm.toggleOnlyAvailable()
        assertEquals(3, vm.state.resultCount)
    }

    @Test fun countryFilterTogglesAndClears() = runTest {
        val vm = vm(env())
        vm.selectCountry("FR")
        assertEquals(listOf("Poupée"), vm.state.groups.flatMap { g -> g.results.map { it.item.title } })
        vm.selectCountry("FR")
        assertNull(vm.state.filters.country)
        vm.selectCountry("CH")
        vm.selectCountry(null)
        assertNull(vm.state.filters.country)
        assertEquals(3, vm.state.resultCount)
    }

    @Test fun nothingFoundIsEmptyOnceLoaded() = runTest {
        val vm = vm(env())
        assertFalse(vm.state.isEmpty)
        vm.onQueryChange("zzz")
        assertTrue(vm.state.isEmpty)
        assertTrue(vm.state.hasQuery)
    }

    @Test fun oneChildFailingDoesNotBlockTheOthers() = runTest {
        val env = env { items.failingChildren += uuid(401) }
        val vm = vm(env)
        assertTrue(vm.state.hasLoaded)
        assertEquals(listOf("Zoé"), vm.state.groups.map { it.child.firstName })
        assertEquals(GiftError.Network, vm.state.error)
        vm.dismissError()
        assertNull(vm.state.error)
    }

    @Test fun aFailedReloadKeepsWhatWasKnownOfThatChild() = runTest {
        val env = env()
        val vm = vm(env)
        env.items.failingChildren += uuid(401)
        env.session.itemsChanged()
        assertEquals(listOf("Léa", "Zoé"), vm.state.groups.map { it.child.firstName })
        assertEquals(GiftError.Network, vm.state.error)
    }

    @Test fun reloadsWhenGiftsChange() = runTest {
        val env = env()
        val vm = vm(env)
        env.items.items += wishItem(2, childN = 1, title = "Vélo", status = null)
        env.session.itemsChanged()
        assertEquals(4, vm.state.resultCount)
    }

    @Test fun reloadsWhenAChildJoinsTheFamily() = runTest {
        val env = env()
        val vm = vm(env)
        val newcomer = testChild(7, householdN = 2, name = "Noé")
        env.f.childRepo.all += newcomer
        env.items.items += wishItem(31, childN = 7, title = "Ballon")
        env.session.refreshAll()
        runCurrent()
        assertTrue(vm.state.groups.any { it.child.firstName == "Noé" })
    }

    @Test fun pullToRefreshReloads() = runTest {
        val env = env()
        val vm = vm(env)
        val calls = env.items.childItemsCalls
        env.items.items += wishItem(2, childN = 1, title = "Vélo", status = null)
        vm.refresh()
        runCurrent()
        assertEquals(calls + 2, env.items.childItemsCalls)
        assertFalse(vm.state.isRefreshing)
        assertEquals(4, vm.state.resultCount)
    }
}
