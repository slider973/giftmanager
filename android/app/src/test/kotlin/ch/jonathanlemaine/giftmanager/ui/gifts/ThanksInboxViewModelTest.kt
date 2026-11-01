package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.FakeThanksRepository
import ch.jonathanlemaine.giftmanager.data.thanksNote
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThanksInboxViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsTheNotes() = runTest {
        val repo = FakeThanksRepository().apply { notes = listOf(thanksNote(1), thanksNote(2)) }
        val vm = ThanksInboxViewModel(repo)
        assertTrue(vm.uiState.value.hasLoaded)
        assertEquals(2, vm.uiState.value.notes.size)
        assertFalse(vm.uiState.value.isEmpty)
    }

    @Test fun anEmptyInboxIsEmptyOnceLoaded() = runTest {
        val vm = ThanksInboxViewModel(FakeThanksRepository())
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test fun aFailedLoadStillMarksLoadedAndReportsTheError() = runTest {
        val repo = FakeThanksRepository().apply { failure = IOException() }
        val vm = ThanksInboxViewModel(repo)
        assertTrue(vm.uiState.value.hasLoaded)
        assertEquals(GiftError.Network, vm.uiState.value.error)
        vm.dismissError()
        assertEquals(null, vm.uiState.value.error)
    }

    @Test fun refreshReloads() = runTest {
        val repo = FakeThanksRepository()
        val vm = ThanksInboxViewModel(repo)
        repo.notes = listOf(thanksNote(1))
        vm.refresh()
        assertEquals(1, vm.uiState.value.notes.size)
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(2, repo.myThanksCalls)
    }

    @Test fun revealingAppliesToEveryNoteOfTheSameGift() = runTest {
        val repo = FakeThanksRepository().apply {
            notes = listOf(thanksNote(1, itemN = 5), thanksNote(2, itemN = 5), thanksNote(3, itemN = 6))
        }
        val vm = ThanksInboxViewModel(repo)
        vm.setRevealed(vm.uiState.value.notes[0], true)
        assertEquals(listOf(true, true, false), vm.uiState.value.notes.map { it.revealed })
        assertEquals(listOf(uuid(705) to true), repo.reveals)
        assertTrue(vm.uiState.value.pending.isEmpty())
    }

    @Test fun aSecondTapIsIgnoredWhileTheFirstIsPending() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeThanksRepository().apply { notes = listOf(thanksNote(1)); revealGate = gate }
        val vm = ThanksInboxViewModel(repo)
        val note = vm.uiState.value.notes[0]
        vm.setRevealed(note, true)
        assertEquals(setOf(note.id), vm.uiState.value.pending)
        vm.setRevealed(note, false)
        assertEquals(1, repo.reveals.size)
        assertTrue(vm.uiState.value.notes[0].revealed)
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value.pending.isEmpty())
        // Une fois l'enregistrement fini, le bouton répond à nouveau.
        vm.setRevealed(vm.uiState.value.notes[0], false)
        assertEquals(2, repo.reveals.size)
    }

    @Test fun aFailureRestoresTheChoiceAndReportsTheError() = runTest {
        val repo = FakeThanksRepository().apply {
            notes = listOf(thanksNote(1, itemN = 5), thanksNote(2, itemN = 5))
            revealFailure = IOException()
        }
        val vm = ThanksInboxViewModel(repo)
        vm.setRevealed(vm.uiState.value.notes[0], true)
        assertEquals(listOf(false, false), vm.uiState.value.notes.map { it.revealed })
        assertEquals(GiftError.Network, vm.uiState.value.error)
        assertTrue(vm.uiState.value.pending.isEmpty())
    }
}
