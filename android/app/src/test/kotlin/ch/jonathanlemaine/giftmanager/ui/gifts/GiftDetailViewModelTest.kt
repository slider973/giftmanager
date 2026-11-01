package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.PotParticipant
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.wishItem
import java.math.BigDecimal
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Règles de la fiche cadeau, vérifiées sans réseau (`GiftDetailModelTests.swift`). Le point sensible est le secret :
 * un parent ne doit jamais apprendre, par un statut ou une liste de participants, que son enfant reçoit tel cadeau.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GiftDetailViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private class Unavailable : Exception("ITEM_UNAVAILABLE")

    private val zoeItem = 1 // cadeau de Zoé (autre foyer) : vue d'un membre
    private val leaItem = 2 // cadeau de Léa (mon enfant) : vue parent

    private fun TestScope.vm(env: GiftsEnv, n: Int) =
        GiftDetailViewModel(uuid(700 + n), env.session, env.items, env.reservations, env.pots, env.notifier, Fixtures.clock)
            .also { runCurrent() }

    private suspend fun TestScope.member(item: (Int) -> WishItem = { wishItem(it, childN = 3) }, setup: GiftsEnv.() -> Unit = {}): Pair<GiftsEnv, GiftDetailViewModel> {
        val env = giftsEnv { items.items += item(zoeItem); setup() }
        return env to vm(env, zoeItem)
    }

    private suspend fun TestScope.parent(item: (Int) -> WishItem = { wishItem(it, childN = 1, status = null) }, setup: GiftsEnv.() -> Unit = {}): Pair<GiftsEnv, GiftDetailViewModel> {
        val env = giftsEnv { items.items += item(leaItem); setup() }
        return env to vm(env, leaItem)
    }

    // region Chargement

    @Test fun loadsTheItemByItsIdAlone() = runTest {
        val (env, vm) = member()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(uuid(701), state.item?.id)
        assertEquals(env.zoe, state.child)
        assertFalse(state.isParentView)
    }

    @Test fun missingItemClosesTheScreenWithNotFound() = runTest {
        val env = giftsEnv()
        val vm = vm(env, 9)
        assertTrue(vm.uiState.value.isClosed)
        assertEquals(GiftError.NotFound, vm.uiState.value.error)
    }

    // endregion

    // region Réservation

    @Test fun reservingMakesTheGiftMineAndWarnsOthers() = runTest {
        val (env, vm) = member()
        val before = env.revision
        vm.reserve()
        val state = vm.uiState.value
        assertEquals(ReservationState.RESERVED, state.item?.myReservation)
        assertEquals(ItemStatus.MINE, state.item?.status)
        assertEquals(Celebration.RESERVED, state.celebration)
        assertEquals(listOf("reserve"), env.reservations.calls)
        assertEquals(listOf(uuid(701)), env.notifier.reservations)
        assertEquals(before + 1, env.revision)
    }

    @Test fun celebrationIsConsumable() = runTest {
        val (_, vm) = member()
        vm.reserve()
        vm.consumeCelebration()
        assertNull(vm.uiState.value.celebration)
    }

    @Test fun cancellingFreesTheGift() = runTest {
        val (env, vm) = member(item = { wishItem(it, childN = 3, status = ItemStatus.MINE, myReservation = ReservationState.RESERVED) })
        vm.cancelReservation()
        assertNull(vm.uiState.value.item?.myReservation)
        assertEquals(ItemStatus.AVAILABLE, vm.uiState.value.item?.status)
        assertEquals(listOf("cancel"), env.reservations.calls)
    }

    @Test fun aParentSeesNoStatusAfterCancelling() = runTest {
        // Le secret : afficher « disponible » à un parent lui apprendrait qu'un cadeau vient d'être libéré.
        val (_, vm) = parent(item = { wishItem(it, childN = 1, status = null, myReservation = ReservationState.RESERVED) })
        vm.cancelReservation()
        assertNull(vm.uiState.value.item?.status)
        assertNull(vm.uiState.value.item?.myReservation)
    }

    @Test fun markingPurchased() = runTest {
        val (env, vm) = member(item = { wishItem(it, childN = 3, status = ItemStatus.MINE, myReservation = ReservationState.RESERVED) })
        vm.setPurchased(true)
        assertEquals(ReservationState.PURCHASED, vm.uiState.value.item?.myReservation)
        assertTrue("setPurchased(true)" in env.reservations.calls)
        vm.setPurchased(false)
        assertEquals(ReservationState.RESERVED, vm.uiState.value.item?.myReservation)
    }

    // endregion

    // region Conflit : quelqu'un a réservé avant moi

    @Test fun giftAlreadyTakenBecomesTaken() = runTest {
        val (env, vm) = member { reservations.failure = Unavailable() }
        vm.reserve()
        val state = vm.uiState.value
        assertEquals(ItemStatus.TAKEN, state.item?.status)
        assertNull("la réservation ne doit pas être appliquée en cas d'échec", state.item?.myReservation)
        assertEquals(GiftError.Unavailable, state.error)
        assertTrue("aucune alerte aux autres foyers si la réservation a échoué", env.notifier.reservations.isEmpty())
        assertNull(state.celebration)
    }

    @Test fun aParentDoesNotSeeTheTakenStatusEither() = runTest {
        val (_, vm) = parent { reservations.failure = Unavailable() }
        vm.reserve()
        assertNull(vm.uiState.value.item?.status)
    }

    @Test fun theActivityIndicatorFallsBackEvenAfterAnError() = runTest {
        val (_, vm) = member { reservations.failure = Unavailable() }
        vm.reserve()
        assertFalse(vm.uiState.value.isWorking)
    }

    @Test fun errorCanBeDismissed() = runTest {
        val (_, vm) = member { reservations.failure = Unavailable() }
        vm.reserve()
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    // endregion

    // region Favori et possession

    @Test fun togglingTheFavorite() = runTest {
        val (env, vm) = parent()
        vm.toggleFavorite()
        assertTrue(vm.uiState.value.item!!.isFavorite)
        vm.toggleFavorite()
        assertFalse(vm.uiState.value.item!!.isFavorite)
        assertEquals(listOf(uuid(702) to true, uuid(702) to false), env.items.priorities)
    }

    @Test fun favoriteDoesNotChangeIfTheCallFails() = runTest {
        val (env, vm) = parent()
        env.items.failure = Unavailable()
        vm.toggleFavorite()
        assertFalse(vm.uiState.value.item!!.isFavorite)
        assertNotNull(vm.uiState.value.error)
    }

    @Test fun togglingOwned() = runTest {
        val (env, vm) = parent()
        vm.toggleOwned()
        assertTrue(vm.uiState.value.item!!.owned)
        assertEquals(listOf(uuid(702) to true), env.items.ownedCalls)
    }

    @Test fun markReceivedOpensTheThanks() = runTest {
        val (_, vm) = parent()
        vm.markReceived()
        assertTrue(vm.uiState.value.item!!.owned)
        assertTrue(vm.uiState.value.thanksRequested)
        vm.consumeThanksRequest()
        assertFalse(vm.uiState.value.thanksRequested)
    }

    @Test fun markReceivedOpensNothingIfTheCallFails() = runTest {
        val (env, vm) = parent()
        env.items.failure = Unavailable()
        vm.markReceived()
        assertFalse(vm.uiState.value.item!!.owned)
        assertFalse(vm.uiState.value.thanksRequested)
    }

    // endregion

    // region Suppression

    @Test fun deletingClosesTheSheet() = runTest {
        val (env, vm) = parent()
        vm.delete()
        assertTrue(vm.uiState.value.isClosed)
        assertEquals(listOf(uuid(702)), env.items.deleted)
    }

    @Test fun deletingDoesNotCloseIfTheCallFails() = runTest {
        val (env, vm) = parent()
        env.items.failure = Unavailable()
        vm.delete()
        assertFalse(vm.uiState.value.isClosed)
    }

    // endregion

    // region Cagnotte : le parent ne doit rien savoir

    private val kenny = PotParticipant("Kenny", BigDecimal(20), "EUR", false)

    @Test fun aParentNeverLoadsTheParticipants() = runTest {
        val (env, vm) = parent(
            setup = { pots.participants = listOf(kenny) },
            item = { wishItem(it, childN = 1, status = null, myContribution = BigDecimal.TEN) },
        )
        assertTrue(vm.uiState.value.participants.isEmpty())
        assertFalse("aucun appel ne doit partir pour un parent", "participants" in env.pots.calls)
        assertFalse(vm.uiState.value.reservation!!.showPot)
        assertNull(vm.uiState.value.potProgress)
    }

    @Test fun participantsLoadWhenIContribute() = runTest {
        val (_, vm) = member(
            setup = { pots.participants = listOf(kenny) },
            item = { wishItem(it, childN = 3, status = ItemStatus.POT, myContribution = BigDecimal.TEN) },
        )
        assertEquals(listOf(kenny), vm.uiState.value.participants)
    }

    @Test fun noParticipantsIfIDoNotContribute() = runTest {
        val (env, vm) = member(item = { wishItem(it, childN = 3, status = ItemStatus.POT) })
        assertTrue(vm.uiState.value.participants.isEmpty())
        assertFalse("participants" in env.pots.calls)
    }

    @Test fun leavingThePotReloadsTheGift() = runTest {
        val (env, vm) = member(item = { wishItem(it, childN = 3, status = ItemStatus.POT, myContribution = BigDecimal.TEN) })
        val loads = env.items.childItemsCalls
        vm.leavePot()
        assertTrue("leave" in env.pots.calls)
        assertTrue(env.items.childItemsCalls > loads)
    }

    // endregion

    // region Confidentialité

    @Test fun parentViewOffersNoReservationActionAndShowsTheSurpriseNotice() = runTest {
        val (_, vm) = parent()
        val actions = vm.uiState.value.reservation!!
        assertTrue(vm.uiState.value.isParentView)
        assertNull(actions.badge)
        assertTrue(actions.surpriseNotice)
        assertFalse(actions.canReserve)
        assertFalse(actions.canStartPot)
        assertFalse(actions.showPot)
        // Seule l'offre discrète du parent (menu) reste possible.
        assertTrue(vm.uiState.value.canParentReserve)
    }

    @Test fun anItemOfAChildOutsideTheFamilyIsAParentViewWhenTheServerSendsNoStatus() = runTest {
        val env = giftsEnv { items.items += wishItem(1, childN = 9, status = null) }
        val vm = vm(env, 1)
        val state = vm.uiState.value
        assertNull("enfant inconnu de la famille courante", state.child)
        assertTrue(state.isParentView)
        assertNull(state.reservation!!.badge)
        assertFalse(state.reservation!!.canReserve)
        assertTrue(state.reservation!!.surpriseNotice)
    }

    @Test fun anItemOfAChildOutsideTheFamilyIsAMemberViewWhenTheServerSendsAStatus() = runTest {
        val env = giftsEnv { items.items += wishItem(1, childN = 9, status = ItemStatus.AVAILABLE) }
        val vm = vm(env, 1)
        assertFalse(vm.uiState.value.isParentView)
        assertTrue(vm.uiState.value.reservation!!.canReserve)
    }

    @Test fun theViewIsParentByDefaultWhenNothingIsLoaded() = runTest {
        val env = giftsEnv()
        val vm = vm(env, 9)
        // Aucun cadeau chargé : on ne montre rien de plus que la vue parent.
        assertTrue(vm.uiState.value.isParentView)
        assertNull(vm.uiState.value.reservation)
        assertFalse(vm.uiState.value.canParentReserve)
    }

    @Test fun receiptActionsFollowTheEventDate() = runTest {
        val (_, past) = parent(item = { wishItem(it, childN = 1, status = null, eventId = giftsEventPast) })
        assertTrue(past.uiState.value.canMarkReceived)
        assertTrue(past.uiState.value.readOnly)
        assertFalse(past.uiState.value.canEdit)
    }

    // endregion

    private val giftsEventPast get() = uuid(502)
}
