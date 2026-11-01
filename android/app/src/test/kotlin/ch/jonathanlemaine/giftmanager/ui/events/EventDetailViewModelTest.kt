package ch.jonathanlemaine.giftmanager.ui.events

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.ReservationCounts
import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.testChild
import ch.jonathanlemaine.giftmanager.core.session.testEvent
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import java.io.IOException
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
class EventDetailViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    // Mon foyer : Léa (enfant), Moi (adulte). Foyer 2 : Zoé (enfant), Mamie (adulte).
    private val lea = testChild(1, name = "Léa")
    private val moi = testChild(2, name = "Moi", adult = true)
    private val zoe = testChild(3, householdN = 2, name = "Zoé", birthdate = "2018-11-10")
    private val mamie = testChild(4, householdN = 2, name = "Mamie", adult = true, birthdate = null)
    private val christmas = testEvent(1, "2026-12-25", kind = GiftEventKind.CHRISTMAS)

    private suspend fun TestScope.fixture(setup: SessionFixture.() -> Unit = {}): SessionFixture {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(uuid(1)))
        f.seedReady()
        f.childRepo.all += listOf(lea, moi, zoe, mamie)
        f.eventRepo.all += christmas
        f.eventRepo.counts = listOf(
            ReservationCounts(lea.id, 9, 9, 9),
            ReservationCounts(zoe.id, 2, 1, 0),
            ReservationCounts(mamie.id, 0, 0, 0),
        )
        f.setup()
        f.session.start()
        runCurrent()
        return f
    }

    private fun vm(f: SessionFixture, id: java.util.UUID = christmas.id) = EventDetailViewModel(id, f.session, f.eventRepo, Fixtures.clock)

    @Test fun christmasSectionsAndCountdown() = runTest {
        val s = vm(fixture()).uiState.value
        assertEquals(christmas, s.event)
        assertFalse(s.isPast)
        assertEquals(71, s.daysRemaining)
        assertEquals(listOf("Zoé"), s.otherKids.map { it.child.firstName })
        assertEquals(listOf("Mamie"), s.otherAdults.map { it.child.firstName })
        assertEquals(listOf("Léa", "Moi"), s.mine.map { it.child.firstName })
        assertTrue(s.mineHasAdult)
        assertFalse(s.isEmpty)
        assertNull(s.birthday)
        assertFalse(s.canEdit)
    }

    @Test fun balanceCountersAreShownForOthersAndNeverForMyLists() = runTest {
        val s = vm(fixture()).uiState.value
        assertEquals(ReservationCounts(zoe.id, 2, 1, 0), s.otherKids.single().counts)
        // Aucun cadeau prévu : le compteur à zéro est conservé (message d'invitation).
        assertEquals(0, s.otherAdults.single().counts?.total)
        assertTrue(s.mine.all { it.counts == null })
    }

    @Test fun missingCounterMeansNothingToShow() = runTest {
        val f = fixture { eventRepo.counts = emptyList() }
        assertNull(vm(f).uiState.value.otherKids.single().counts)
    }

    @Test fun countersAreRequestedForThisEvent() = runTest {
        val f = fixture()
        vm(f)
        assertEquals(listOf<java.util.UUID?>(christmas.id), f.eventRepo.countsCalls)
    }

    @Test fun pastEventHasNoCountersAndIsReadOnly() = runTest {
        val past = testEvent(2, "2026-10-01")
        val f = fixture { eventRepo.all += past }
        val vm = vm(f, past.id)
        val s = vm.uiState.value
        assertTrue(s.isPast)
        assertTrue(s.readOnly)
        assertFalse(s.canEdit)
        assertTrue(f.eventRepo.countsCalls.isEmpty())
        assertTrue((s.otherKids + s.otherAdults).all { it.counts == null })
    }

    @Test fun birthdayShowsAgeOnlyOfTheBirthdayChild() = runTest {
        val birthday = testEvent(3, "2026-11-10", kind = GiftEventKind.BIRTHDAY, childId = zoe.id)
        val f = fixture { eventRepo.all += birthday }
        val s = vm(f, birthday.id).uiState.value
        assertEquals(BirthdayInfo("Zoé", 8, isPast = false), s.birthday)
        assertEquals(listOf("Zoé"), s.otherKids.map { it.child.firstName })
        assertTrue(s.otherAdults.isEmpty())
        assertTrue(s.mine.isEmpty())
    }

    @Test fun pastBirthdayIsInThePastTense() = runTest {
        val birthday = testEvent(3, "2026-10-10", kind = GiftEventKind.BIRTHDAY, childId = zoe.id)
        val f = fixture { eventRepo.all += birthday }
        assertEquals(BirthdayInfo("Zoé", 7, isPast = true), vm(f, birthday.id).uiState.value.birthday)
    }

    @Test fun birthdayWithoutBirthdateHasNoLine() = runTest {
        val birthday = testEvent(3, "2026-11-10", kind = GiftEventKind.BIRTHDAY, childId = mamie.id)
        val f = fixture { eventRepo.all += birthday }
        assertNull(vm(f, birthday.id).uiState.value.birthday)
    }

    @Test fun otherEventIsEditableWhenUpcoming() = runTest {
        val other = testEvent(4, "2026-11-20", createdBy = uuid(1))
        val f = fixture { eventRepo.all += other }
        assertTrue(vm(f, other.id).uiState.value.canEdit)
    }

    @Test fun mineOnlyKidsUseTheKidsTitle() = runTest {
        val f = fixture { childRepo.all.remove(moi) }
        assertFalse(vm(f).uiState.value.mineHasAdult)
    }

    @Test fun eventWithoutAnyListIsEmpty() = runTest {
        val f = fixture { childRepo.all.clear() }
        assertTrue(vm(f).uiState.value.isEmpty)
    }

    @Test fun closesWhenTheEventDisappears() = runTest {
        val f = fixture()
        val vm = vm(f)
        assertFalse(vm.uiState.value.isClosed)
        f.eventRepo.all.clear()
        f.session.reloadGroup()
        runCurrent()
        assertTrue(vm.uiState.value.isClosed)
    }

    @Test fun unknownEventIsClosed() = runTest {
        assertTrue(vm(fixture(), uuid(999)).uiState.value.isClosed)
    }

    @Test fun countersReloadWhenGiftsChange() = runTest {
        val f = fixture()
        val vm = vm(f)
        f.eventRepo.counts = listOf(ReservationCounts(zoe.id, 5, 0, 0))
        f.session.itemsChanged()
        runCurrent()
        assertEquals(5, vm.uiState.value.otherKids.single().counts?.reservedCount)
        assertEquals(2, f.eventRepo.countsCalls.size)
    }

    @Test fun counterFailureReportsAnError() = runTest {
        val f = fixture { eventRepo.countsFailure = IOException("réseau") }
        val vm = vm(f)
        assertEquals(GiftError.Network, vm.uiState.value.error)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test fun refreshReloadsTheGroupAndTheCounters() = runTest {
        val f = fixture()
        val vm = vm(f)
        f.childRepo.all += testChild(9, householdN = 2, name = "Noé")
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(listOf("Noé", "Zoé").sorted(), vm.uiState.value.otherKids.map { it.child.firstName }.sorted())
        assertEquals(2, f.eventRepo.countsCalls.size)
    }
}
