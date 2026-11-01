package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.PendingIdea
import ch.jonathanlemaine.giftmanager.core.session.uuid
import java.time.Instant
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
class PendingIdeasViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun idea(n: Int) = PendingIdea(
        id = uuid(700 + n), childId = uuid(401), childName = "Léa", title = "Idée $n", createdBy = uuid(2),
        authorName = "Marie", createdAt = Instant.parse("2026-10-01T10:00:00Z"),
    )

    private suspend fun TestScope.env(vararg ideas: PendingIdea) = giftsEnv { items.pending = ideas.toList() }

    private fun TestScope.vm(env: GiftsEnv) =
        PendingIdeasViewModel(env.session, env.items, env.notifier).also { runCurrent() }

    @Test fun loadsPendingIdeas() = runTest {
        val vm = vm(env(idea(1), idea(2)))
        val s = vm.uiState.value
        assertTrue(s.hasLoaded)
        assertEquals(listOf(uuid(701), uuid(702)), s.ideas.map { it.id })
        assertNull(s.workingId)
    }

    @Test fun emptyListIsLoaded() = runTest {
        val s = vm(env()).uiState.value
        assertTrue(s.hasLoaded)
        assertTrue(s.ideas.isEmpty())
    }

    @Test fun loadFailureIsReportedButMarksLoaded() = runTest {
        val env = env(idea(1))
        env.items.failure = java.io.IOException("réseau")
        val s = vm(env).uiState.value
        assertTrue(s.hasLoaded)
        assertEquals(GiftError.Network, s.error)
    }

    @Test fun acceptRemovesTheIdeaAndNotifies() = runTest {
        val env = env(idea(1), idea(2))
        val vm = vm(env)
        val before = env.revision
        vm.accept(idea(1))
        runCurrent()
        assertEquals(listOf(Triple(uuid(701), IdeaReview.ACCEPTED, null as String?)), env.items.reviews)
        assertEquals(listOf(uuid(701) to IdeaReview.ACCEPTED), env.notifier.reviews)
        assertEquals(before + 1, env.revision)
        assertEquals(listOf(uuid(702)), vm.uiState.value.ideas.map { it.id })
        assertNull(vm.uiState.value.workingId)
    }

    @Test fun ownedAlreadyUsesItsOwnVerdict() = runTest {
        val env = env(idea(1))
        val vm = vm(env)
        vm.ownedAlready(idea(1))
        runCurrent()
        assertEquals(IdeaReview.OWNED_ALREADY, env.items.reviews.single().second)
        assertEquals(IdeaReview.OWNED_ALREADY, env.notifier.reviews.single().second)
    }

    @Test fun rejectTrimsTheNoteAndDropsBlankOnes() = runTest {
        val env = env(idea(1), idea(2))
        val vm = vm(env)
        vm.reject(idea(1), "  Trop cher  ")
        runCurrent()
        vm.reject(idea(2), "   ")
        runCurrent()
        assertEquals(listOf("Trop cher", null), env.items.reviews.map { it.third })
        assertTrue(env.items.reviews.all { it.second == IdeaReview.REJECTED })
        assertTrue(vm.uiState.value.ideas.isEmpty())
    }

    @Test fun failureKeepsTheIdeaAndDoesNotNotify() = runTest {
        val env = env(idea(1))
        val vm = vm(env)
        val before = env.revision
        env.items.failure = java.io.IOException("réseau")
        vm.accept(idea(1))
        runCurrent()
        val s = vm.uiState.value
        assertEquals(listOf(uuid(701)), s.ideas.map { it.id })
        assertEquals(GiftError.Network, s.error)
        assertNull(s.workingId)
        assertTrue(env.notifier.reviews.isEmpty())
        assertEquals(before, env.revision)
        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test fun reloadsWhenItemsChange() = runTest {
        val env = env(idea(1))
        val vm = vm(env)
        env.items.pending = listOf(idea(1), idea(3))
        env.session.itemsChanged()
        runCurrent()
        assertEquals(2, vm.uiState.value.ideas.size)
        assertFalse(vm.uiState.value.ideas.isEmpty())
    }
}
