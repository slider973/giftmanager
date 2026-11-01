package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.Fixtures
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.uuid
import ch.jonathanlemaine.giftmanager.data.wishItem
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
class ThanksComposerViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val leaItem = 2

    private suspend fun TestScope.setup(setup: GiftsEnv.() -> Unit = {}): Pair<GiftsEnv, ThanksComposerViewModel> {
        val env = giftsEnv { items.items += wishItem(leaItem, childN = 1, status = null, owned = true, title = "Lego"); setup() }
        return env to vm(env, leaItem)
    }

    private fun TestScope.vm(env: GiftsEnv, n: Int) =
        ThanksComposerViewModel(uuid(700 + n), env.session, env.items, env.thanks, env.images, env.notifier, Fixtures.clock)
            .also { runCurrent() }

    @Test fun loadsTheGiftTheChildNameAndPrefillsTheMessage() = runTest {
        val (_, vm) = setup()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Lego", state.item?.title)
        assertEquals("Léa", state.childName)
        assertEquals("Merci beaucoup pour « Lego » !", state.message)
        assertTrue(state.canSend)
    }

    @Test fun missingGiftClosesTheScreen() = runTest {
        val env = giftsEnv()
        val vm = vm(env, 9)
        assertTrue(vm.uiState.value.isClosed)
        assertEquals(GiftError.NotFound, vm.uiState.value.error)
    }

    @Test fun anEmptyMessageCannotBeSentAndNothingIsCalled() = runTest {
        val (env, vm) = setup()
        vm.onMessageChange("   ")
        assertFalse(vm.uiState.value.canSend)
        vm.send()
        assertTrue(env.thanks.sent.isEmpty())
    }

    @Test fun aTooLongMessageCannotBeSent() = runTest {
        val (env, vm) = setup()
        vm.onMessageChange("a".repeat(1001))
        assertTrue(vm.uiState.value.isTooLong)
        vm.send()
        assertTrue(env.thanks.sent.isEmpty())
    }

    @Test fun sendsTheTrimmedMessageThenNotifiesWithoutAPhoto() = runTest {
        val (env, vm) = setup()
        vm.onMessageChange("  Merci Mamie !  ")
        vm.send()
        assertEquals(listOf(Triple(uuid(702), "Merci Mamie !", null)), env.thanks.sent)
        assertEquals(0, env.images.uploads)
        assertEquals(listOf(uuid(702)), env.notifier.thanks)
        assertTrue(vm.uiState.value.sent)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test fun thePhotoIsUploadedBeforeTheThanksAndItsUrlIsSent() = runTest {
        val (env, vm) = setup()
        vm.onPhotoPicked(byteArrayOf(1, 2, 3))
        vm.send()
        assertEquals(1, env.images.uploads)
        assertEquals("https://example.test/image.jpg", env.thanks.sent.single().third)
        assertTrue(vm.uiState.value.sent)
    }

    @Test fun aFailedUploadSendsNothingAndShowsTheError() = runTest {
        val (env, vm) = setup()
        env.images.failure = IOException()
        vm.onPhotoPicked(byteArrayOf(1))
        vm.send()
        assertTrue(env.thanks.sent.isEmpty())
        assertTrue(env.notifier.thanks.isEmpty())
        assertEquals(GiftError.Network, vm.uiState.value.error)
        assertFalse(vm.uiState.value.sent)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test fun aFailedSendKeepsTheMessageWithoutNotifyingAndAllowsARetryWithoutUploadingAgain() = runTest {
        val (env, vm) = setup()
        env.thanks.sendFailure = IOException()
        vm.onMessageChange("Merci")
        vm.onPhotoPicked(byteArrayOf(1))
        vm.send()
        assertEquals(GiftError.Network, vm.uiState.value.error)
        assertEquals("Merci", vm.uiState.value.message)
        assertTrue(env.notifier.thanks.isEmpty())
        env.thanks.sendFailure = null
        vm.send()
        assertEquals(1, env.images.uploads)
        assertTrue(vm.uiState.value.sent)
        assertNull(vm.uiState.value.error)
    }

    @Test fun removingThePhotoSendsNone() = runTest {
        val (env, vm) = setup()
        vm.onPhotoPicked(byteArrayOf(1))
        vm.onPhotoRemoved()
        vm.send()
        assertEquals(0, env.images.uploads)
        assertNull(env.thanks.sent.single().third)
    }

    @Test fun aSecondTapIsIgnoredOnceSent() = runTest {
        val (env, vm) = setup()
        vm.send()
        vm.send()
        assertEquals(1, env.thanks.sent.size)
        assertEquals(1, env.notifier.thanks.size)
    }
}
