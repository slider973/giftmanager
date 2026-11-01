package ch.jonathanlemaine.giftmanager.ui.events

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.session.SessionFixture
import ch.jonathanlemaine.giftmanager.core.session.testChild
import ch.jonathanlemaine.giftmanager.core.session.testEvent
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import java.io.IOException
import java.time.LocalDate
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
class EventEditorViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val me = uuid(1)

    private suspend fun TestScope.fixture(setup: SessionFixture.() -> Unit = { seedReady() }): SessionFixture {
        val f = SessionFixture(backgroundScope, AuthState.SignedIn(me))
        f.setup()
        f.session.start()
        runCurrent()
        return f
    }

    private fun vm(f: SessionFixture, mode: EventEditorMode) =
        EventEditorViewModel(mode, f.session, f.eventRepo, Fixtures.clock) { uuid(901) }

    @Test fun createStartsToday() = runTest {
        val s = vm(fixture(), EventEditorMode.Create).uiState.value
        assertFalse(s.isEditing)
        assertEquals(LocalDate.of(2026, 10, 15), s.date)
        assertFalse(s.canSave)
        assertFalse(s.canDelete)
    }

    @Test fun titleIsLimitedTo80Characters() = runTest {
        val vm = vm(fixture(), EventEditorMode.Create)
        vm.onTitleChange("t".repeat(120))
        assertEquals(80, vm.uiState.value.title.length)
    }

    @Test fun createSavesAnOtherEventInTheCurrentGroup() = runTest {
        val f = fixture()
        val vm = vm(f, EventEditorMode.Create)
        vm.onTitleChange("  Fête de fin d'année ")
        vm.onDateChange(LocalDate.of(2026, 12, 5))
        vm.save()
        runCurrent()
        val saved = f.eventRepo.saved.single()
        assertEquals(
            GiftEvent(uuid(901), uuid(101), GiftEventKind.OTHER, "Fête de fin d'année", DayDate(LocalDate.of(2026, 12, 5)), null, null),
            saved,
        )
        assertTrue(vm.uiState.value.finished)
        // reloadGroup : l'événement est dans la session.
        assertEquals(listOf(uuid(901)), f.session.state.value.events.map { it.id })
    }

    @Test fun blankTitleCannotBeSaved() = runTest {
        val f = fixture()
        val vm = vm(f, EventEditorMode.Create)
        vm.onTitleChange("  ")
        vm.save()
        runCurrent()
        assertTrue(f.eventRepo.saved.isEmpty())
    }

    @Test fun editKeepsIdAndCreatedBy() = runTest {
        val event = testEvent(1, "2026-11-20", createdBy = me)
        val f = fixture { seedReady(); eventRepo.all += event }
        val vm = vm(f, EventEditorMode.Edit(event.id))
        assertTrue(vm.uiState.value.isEditing)
        assertEquals("Événement 1", vm.uiState.value.title)
        assertEquals(LocalDate.of(2026, 11, 20), vm.uiState.value.date)
        vm.onTitleChange("Renommé")
        vm.save()
        runCurrent()
        assertEquals(event.copy(title = "Renommé"), f.eventRepo.saved.single())
    }

    @Test fun editOfUnknownEventFinishesImmediately() = runTest {
        assertTrue(vm(fixture(), EventEditorMode.Edit(uuid(999))).uiState.value.finished)
    }

    @Test fun saveFailureShowsErrorAndStaysOpen() = runTest {
        val f = fixture()
        val vm = vm(f, EventEditorMode.Create)
        vm.onTitleChange("Fête")
        f.eventRepo.failure = IOException("réseau")
        vm.save()
        runCurrent()
        assertEquals(GiftError.Network, vm.uiState.value.error)
        assertFalse(vm.uiState.value.finished)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test fun authorCanDelete() = runTest {
        val event = testEvent(1, "2026-11-20", createdBy = me)
        val f = fixture { seedReady(); eventRepo.all += event }
        val vm = vm(f, EventEditorMode.Edit(event.id))
        assertTrue(vm.uiState.value.canDelete)
        vm.requestDelete()
        assertTrue(vm.uiState.value.confirmDelete)
        vm.delete()
        runCurrent()
        assertEquals(listOf(event.id), f.eventRepo.deleted)
        assertTrue(vm.uiState.value.finished)
        assertTrue(f.session.state.value.events.isEmpty())
    }

    @Test fun nonAuthorCannotDelete() = runTest {
        val event = testEvent(1, "2026-11-20", createdBy = uuid(2))
        val f = fixture { seedReady(); eventRepo.all += event }
        val vm = vm(f, EventEditorMode.Edit(event.id))
        assertFalse(vm.uiState.value.canDelete)
        vm.requestDelete()
        assertFalse(vm.uiState.value.confirmDelete)
        vm.delete()
        runCurrent()
        assertTrue(f.eventRepo.deleted.isEmpty())
    }

    @Test fun cancelDeleteKeepsTheEvent() = runTest {
        val event = testEvent(1, "2026-11-20", createdBy = me)
        val f = fixture { seedReady(); eventRepo.all += event }
        val vm = vm(f, EventEditorMode.Edit(event.id))
        vm.requestDelete()
        vm.cancelDelete()
        assertFalse(vm.uiState.value.confirmDelete)
        assertTrue(f.eventRepo.deleted.isEmpty())
    }

    @Test fun parentCanDeleteABirthdayOfTheirChild() = runTest {
        val child = testChild(1)
        val birthday = testEvent(2, "2026-11-20", kind = GiftEventKind.BIRTHDAY, childId = child.id, createdBy = uuid(3))
        val f = fixture { seedReady(); childRepo.all += child; eventRepo.all += birthday }
        assertTrue(vm(f, EventEditorMode.Edit(birthday.id)).uiState.value.canDelete)
    }
}
