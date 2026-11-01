package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.currency.ExchangeRates
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.gifts.GiftTab
import ch.jonathanlemaine.giftmanager.core.gifts.GiftActionReducer
import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.testChild
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.itemLink
import ch.jonathanlemaine.giftmanager.data.wishItem
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class ChildGiftsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val lea = uuid(401)
    private val zoe = uuid(403)

    // Léa (mon enfant) : trois envies, un cadeau déjà possédé — statut nul, comme le renvoie le serveur à un parent.
    private val leaItems = listOf(
        wishItem(1, childN = 1, status = null, position = 0, eventId = uuid(501)),
        wishItem(2, childN = 1, status = null, position = 1),
        wishItem(3, childN = 1, status = null, position = 2),
        wishItem(4, childN = 1, status = null, owned = true),
    )

    // Zoé (autre foyer) : une envie, une idée de moi, une idée d'un autre.
    private val zoeItems = listOf(
        wishItem(11, childN = 3),
        wishItem(12, childN = 3, kind = WishKind.IDEA, createdBy = uuid(1)),
        wishItem(13, childN = 3, kind = WishKind.IDEA, createdBy = uuid(2)),
    )

    private fun TestScope.vm(
        env: GiftsEnv,
        childId: UUID = lea,
        eventId: UUID? = null,
        readOnly: Boolean = false,
    ) = ChildGiftsViewModel(childId, eventId, readOnly, env.session, env.items).also { runCurrent() }

    private suspend fun TestScope.env(setup: GiftsEnv.() -> Unit = {}) = giftsEnv {
        items.items += leaItems + zoeItems
        items.itemLinks += itemLink(1, 1)
        items.itemLinks += itemLink(2, 11, store = "Amazon", country = "FR")
        setup()
    }

    private val ChildGiftsViewModel.state get() = uiState.value

    // region Mode enfant (#75)

    @Test fun childModeIsOfferedOnMyChildsListWithWishes() = runTest {
        assertTrue(vm(env()).state.canStartChildMode)
    }

    @Test fun childModeIsNotOfferedOnAnotherHouseholdsList() = runTest {
        assertFalse(vm(env(), childId = zoe).state.canStartChildMode)
    }

    @Test fun childModeIsNotOfferedForAPastEvent() = runTest {
        assertFalse(vm(env(), readOnly = true).state.canStartChildMode)
    }

    @Test fun childModeIsNotOfferedOnAnAdultList() = runTest {
        val env = env {
            f.childRepo.all += testChild(5, adult = true, name = "Papa")
            items.items += wishItem(21, childN = 5, status = null)
        }
        assertFalse(vm(env, childId = uuid(405)).state.canStartChildMode)
    }

    @Test fun childModeIsNotOfferedWithoutAnyWish() = runTest {
        val env = giftsEnv { items.items += wishItem(1, childN = 1, status = null, owned = true) }
        assertFalse(vm(env).state.canStartChildMode)
    }

    // endregion

    // region Chargement

    @Test fun loadsItemsAndLinks() = runTest {
        val env = env()
        val vm = vm(env)
        assertTrue(vm.state.hasLoaded)
        assertEquals(4, vm.state.items.size)
        assertEquals(1, vm.state.linksFor(leaItems[0]).size)
        assertEquals("Léa", vm.state.child?.firstName)
        assertFalse(vm.state.isClosed)
    }

    @Test fun reloadsWhenItemsChange() = runTest {
        val env = env()
        val vm = vm(env)
        val calls = env.items.childItemsCalls
        env.items.items += wishItem(5, childN = 1, status = null)
        env.session.itemsChanged()
        assertEquals(calls + 1, env.items.childItemsCalls)
        assertEquals(5, vm.state.items.size)
    }

    @Test fun pullToRefreshReloads() = runTest {
        val env = env()
        val vm = vm(env)
        val calls = env.items.childItemsCalls
        vm.refresh()
        assertEquals(calls + 1, env.items.childItemsCalls)
        assertFalse(vm.state.isRefreshing)
    }

    @Test fun loadFailureIsReported() = runTest {
        val env = env { items.failure = IOException("réseau") }
        val vm = vm(env)
        assertEquals(GiftError.Network, vm.state.error)
        assertFalse(vm.state.isLoading)
        vm.dismissError()
        assertNull(vm.state.error)
    }

    // endregion

    // region Filtre d'événement (#61)

    @Test fun startsOnTheRouteEventAndOffersAllGiftsOption() = runTest {
        val env = env()
        val vm = vm(env, eventId = uuid(501))
        assertEquals(uuid(501), env.items.lastEventFilter)
        assertEquals(uuid(501), vm.state.eventId)

        vm.selectEvent(null)
        assertNull("« Tous les cadeaux » ne filtre rien", env.items.lastEventFilter)
        assertNull(vm.state.eventId)
        assertNull(vm.state.selectedEvent)
    }

    @Test fun selectingTheSameEventDoesNotReload() = runTest {
        val env = env()
        val vm = vm(env, eventId = uuid(501))
        val calls = env.items.childItemsCalls
        vm.selectEvent(uuid(501))
        assertEquals(calls, env.items.childItemsCalls)
    }

    @Test fun selectableEventsAreGlobalOrTheChildsOwn() = runTest {
        val env = env()
        val vm = vm(env)
        assertEquals(listOf(env.past.id, env.christmas.id), vm.state.selectableEvents.map { it.id })
    }

    // endregion

    // region Onglets

    @Test fun partitionAndCounts() = runTest {
        val vm = vm(env())
        assertEquals(3, vm.state.counts.wishes)
        assertEquals(1, vm.state.counts.owned)
        assertEquals(3, vm.state.visibleItems.size)
        vm.selectTab(GiftTab.OWNED)
        assertEquals(listOf(uuid(704)), vm.state.visibleItems.map { it.id })
    }

    @Test fun aParentHasNoIdeasTab() = runTest {
        val vm = vm(env())
        assertTrue(vm.state.isParent)
        assertEquals(listOf(GiftTab.WISHES, GiftTab.OWNED), vm.state.tabs)
        vm.selectTab(GiftTab.IDEAS)
        assertEquals("l'onglet Idées n'existe pas côté parents", GiftTab.WISHES, vm.state.tab)
    }

    @Test fun aMemberSeesTheIdeasTab() = runTest {
        val vm = vm(env(), childId = zoe)
        assertFalse(vm.state.isParent)
        assertEquals(GiftTab.entries, vm.state.tabs)
        vm.selectTab(GiftTab.IDEAS)
        assertEquals(2, vm.state.visibleItems.size)
    }

    @Test fun addModeFollowsTheTab() = runTest {
        val parent = vm(env())
        assertEquals(WishKind.WISH, parent.state.addMode?.kind)
        parent.selectTab(GiftTab.OWNED)
        assertTrue(parent.state.addMode!!.owned)
        val member = vm(env(), childId = zoe)
        assertNull(member.state.addMode)
        member.selectTab(GiftTab.IDEAS)
        assertEquals(WishKind.IDEA, member.state.addMode?.kind)
        assertNull(vm(env(), readOnly = true).state.addMode)
    }

    // endregion

    // region Confidentialité

    @Test fun aParentsListShowsNoBadge() = runTest {
        val vm = vm(env())
        assertTrue(vm.state.items.all { it.status == null })
        assertTrue(vm.state.visibleItems.all { GiftActionReducer.badge(it, vm.state.isParent) == null })
    }

    @Test fun aMembersListShowsTheServerStatus() = runTest {
        val vm = vm(env(), childId = zoe)
        assertEquals(ItemStatus.AVAILABLE, vm.state.visibleItems.first().status)
        assertEquals(
            ch.jonathanlemaine.giftmanager.ui.components.GiftStatus.AVAILABLE,
            GiftActionReducer.badge(vm.state.visibleItems.first(), vm.state.isParent),
        )
    }

    @Test fun aMemberCannotEditOrReorderAParentsWishes() = runTest {
        val env = env()
        val vm = vm(env, childId = zoe)
        val wish = zoeItems[0]
        vm.delete(wish)
        vm.toggleOwned(wish)
        vm.toggleFavorite(wish)
        vm.move(0, 1)
        assertTrue(env.items.deleted.isEmpty())
        assertTrue(env.items.ownedCalls.isEmpty())
        assertTrue(env.items.priorities.isEmpty())
        assertNull(env.items.reordered)
    }

    @Test fun anAuthorCanDeleteHisOwnIdeaOnly() = runTest {
        val env = env()
        val vm = vm(env, childId = zoe)
        vm.delete(zoeItems[2])
        assertTrue(env.items.deleted.isEmpty())
        vm.delete(zoeItems[1])
        assertEquals(listOf(zoeItems[1].id), env.items.deleted)
        assertFalse(vm.state.items.any { it.id == zoeItems[1].id })
    }

    @Test fun aPastEventListIsReadOnly() = runTest {
        val env = env()
        val vm = vm(env, readOnly = true)
        vm.delete(leaItems[0])
        vm.toggleFavorite(leaItems[0])
        vm.move(0, 1)
        assertTrue(env.items.deleted.isEmpty())
        assertTrue(env.items.priorities.isEmpty())
        assertNull(env.items.reordered)
        assertFalse(vm.state.canReorder)
    }

    @Test fun listOfAChildWhoLeftTheFamilyCloses() = runTest {
        val env = env()
        val vm = vm(env, childId = uuid(499))
        assertTrue(vm.state.isClosed)
    }

    // endregion

    // region Actions

    @Test fun deletingRemovesTheGiftAndTellsTheOthers() = runTest {
        val env = env()
        val vm = vm(env)
        val before = env.revision
        vm.delete(leaItems[1])
        assertEquals(listOf(leaItems[1].id), env.items.deleted)
        assertFalse(vm.state.items.any { it.id == leaItems[1].id })
        assertEquals(before + 1, env.revision)
    }

    @Test fun deleteFailureKeepsTheGift() = runTest {
        val env = env()
        val vm = vm(env)
        env.items.failure = IOException("réseau")
        vm.delete(leaItems[1])
        assertTrue(vm.state.items.any { it.id == leaItems[1].id })
        assertEquals(GiftError.Network, vm.state.error)
    }

    @Test fun alreadyOwnedMovesTheGiftOfTab() = runTest {
        val env = env()
        val vm = vm(env)
        vm.toggleOwned(leaItems[0])
        assertEquals(listOf(leaItems[0].id to true), env.items.ownedCalls)
        assertEquals(2, vm.state.counts.wishes)
        assertEquals(2, vm.state.counts.owned)
    }

    @Test fun toggleOwnedIsRolledBackOnFailure() = runTest {
        val env = env()
        val vm = vm(env)
        env.items.failure = IOException("réseau")
        vm.toggleOwned(leaItems[0])
        assertEquals(3, vm.state.counts.wishes)
        assertFalse(vm.state.items.first { it.id == leaItems[0].id }.owned)
        assertEquals(GiftError.Network, vm.state.error)
    }

    @Test fun favoriteIsAppliedThenRolledBackOnFailure() = runTest {
        val env = env()
        val vm = vm(env)
        vm.toggleFavorite(leaItems[0])
        assertTrue(vm.state.items.first { it.id == leaItems[0].id }.isFavorite)
        assertEquals(listOf(leaItems[0].id to true), env.items.priorities)

        env.items.failure = IOException("réseau")
        vm.toggleFavorite(vm.state.items.first { it.id == leaItems[1].id })
        assertFalse(vm.state.items.first { it.id == leaItems[1].id }.isFavorite)
    }

    @Test fun movingReordersAndPassesTheCurrentPositionsSoOnlyChangesAreWritten() = runTest {
        val env = env()
        val vm = vm(env)
        vm.move(0, 2)
        assertEquals(listOf(uuid(702), uuid(703), uuid(701)), env.items.reordered)
        assertEquals(mapOf(uuid(701) to 0, uuid(702) to 1, uuid(703) to 2), env.items.reorderPositions)
        assertEquals(listOf(uuid(702), uuid(703), uuid(701)), vm.state.visibleItems.map { it.id })
        assertEquals(listOf(0, 1, 2), vm.state.visibleItems.map { it.position })
    }

    @Test fun movingIsRolledBackOnFailure() = runTest {
        val env = env()
        val vm = vm(env)
        env.items.failure = IOException("réseau")
        vm.move(0, 2)
        assertEquals(listOf(uuid(701), uuid(702), uuid(703)), vm.state.visibleItems.map { it.id })
        assertEquals(GiftError.Network, vm.state.error)
    }

    @Test fun movingOnTheOwnedTabIsRefused() = runTest {
        val env = env()
        val vm = vm(env).apply { selectTab(GiftTab.OWNED) }
        vm.move(0, 1)
        assertNull(env.items.reordered)
    }

    // endregion

    // region Conversion de devises (#74)

    private val rates = ExchangeRates("EUR", "2026-10-02", mapOf("CHF" to BigDecimal("0.93")), Instant.EPOCH)

    private fun plain(text: String?) = text?.replace('\u00A0', ' ')?.replace('\u202F', ' ')

    @Test fun pricesInAnotherCurrencyGetAnIndicativeApproximation() = runTest {
        // Profil en CHF ; le lien de Léa est à 100 EUR = 93 CHF.
        val env = env { items.itemLinks.clear(); items.itemLinks += itemLink(1, 1).copy(currency = "EUR", price = BigDecimal("100")) }
        val flow = MutableStateFlow<ExchangeRates?>(null)
        val vm = ChildGiftsViewModel(lea, null, false, env.session, env.items, flow).also { runCurrent() }
        val link = vm.state.linksFor(leaItems[0]).single()
        assertEquals("CHF", vm.state.profileCurrency)
        assertNull(vm.state.approxText(link))
        assertNull(vm.state.rateDateText)
        assertFalse(vm.state.hasConversion)
        flow.value = rates
        val approx = plain(vm.state.approxText(link))!!
        assertTrue(approx, approx.startsWith("≈ ") && approx.contains("93") && approx.contains("CHF"))
        assertEquals("2 octobre", vm.state.rateDateText)
        assertTrue(vm.state.hasConversion)
    }

    @Test fun samePriceCurrencyAsTheProfileIsNeverConverted() = runTest {
        val env = env()
        val flow = MutableStateFlow<ExchangeRates?>(rates)
        val vm = ChildGiftsViewModel(lea, null, false, env.session, env.items, flow).also { runCurrent() }
        // Le lien de Léa est en CHF, comme le profil.
        assertNull(vm.state.approxText(vm.state.linksFor(leaItems[0]).single()))
        assertFalse(vm.state.hasConversion)
    }

    // endregion
}
