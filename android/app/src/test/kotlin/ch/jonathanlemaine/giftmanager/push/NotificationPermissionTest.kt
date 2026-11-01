package ch.jonathanlemaine.giftmanager.push

import ch.jonathanlemaine.giftmanager.push.NotificationPermission.CanAsk
import ch.jonathanlemaine.giftmanager.push.NotificationPermission.Granted
import ch.jonathanlemaine.giftmanager.push.NotificationPermission.NotRequired
import ch.jonathanlemaine.giftmanager.push.NotificationPermission.PermanentlyDenied
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionTest {
    private fun resolve(sdk: Int = 34, granted: Boolean = false, rationale: Boolean = false, asked: Boolean = false) =
        NotificationPermission.resolve(sdk, granted, rationale, asked)

    @Test fun noRuntimePermissionBeforeAndroid13() {
        assertEquals(NotRequired, resolve(sdk = 26))
        assertEquals(NotRequired, resolve(sdk = 32, granted = false, asked = true))
    }

    @Test fun grantedOnAndroid13() {
        assertEquals(Granted, resolve(sdk = 33, granted = true))
        assertEquals(Granted, resolve(granted = true, asked = true))
    }

    @Test fun neverAskedCanAsk() {
        assertEquals(CanAsk, resolve(asked = false, rationale = false))
    }

    @Test fun deniedOnceCanAskAgain() {
        assertEquals(CanAsk, resolve(asked = true, rationale = true))
    }

    @Test fun deniedWithoutRationaleAfterAskingIsPermanent() {
        assertEquals(PermanentlyDenied, resolve(asked = true, rationale = false))
    }

    @Test fun onlyAuthorizedStatesAllowTokenRegistration() {
        assertTrue(NotRequired.canRegister)
        assertTrue(Granted.canRegister)
        assertFalse(CanAsk.canRegister)
        assertFalse(PermanentlyDenied.canRegister)
    }

    @Test fun promptOnlyOnceAndOnlyWhenAskable() {
        assertTrue(NotificationPermission.shouldPrompt(CanAsk, alreadyPrompted = false))
        assertFalse(NotificationPermission.shouldPrompt(CanAsk, alreadyPrompted = true))
        assertFalse(NotificationPermission.shouldPrompt(Granted, alreadyPrompted = false))
        assertFalse(NotificationPermission.shouldPrompt(NotRequired, alreadyPrompted = false))
        assertFalse(NotificationPermission.shouldPrompt(PermanentlyDenied, alreadyPrompted = false))
    }

    // region Contrôleur

    private class FakeEnvironment(override val sdk: Int = 34) : NotificationPermissionEnvironment {
        var granted = false
        var rationale = false
        var launches = 0
        override fun isGranted() = granted
        override fun shouldShowRationale() = rationale
        override fun launchRequest() {
            launches++
        }
    }

    @Test fun promptAppearsOnceOnFirstArrival() = runTest {
        val controller = NotificationPermissionController(FakeEnvironment(), FakePushPrefs())
        controller.onMainScreenReached()
        assertTrue(controller.promptVisible.value)
        controller.dismissPrompt()
        controller.onMainScreenReached()
        assertFalse(controller.promptVisible.value)
    }

    @Test fun noPromptWhenAlreadyGrantedOrNotRequired() = runTest {
        val granted = FakeEnvironment().apply { granted = true }
        NotificationPermissionController(granted, FakePushPrefs()).also {
            it.onMainScreenReached()
            assertFalse(it.promptVisible.value)
            assertEquals(Granted, it.permission.value)
        }
        NotificationPermissionController(FakeEnvironment(sdk = 30), FakePushPrefs()).also {
            it.onMainScreenReached()
            assertFalse(it.promptVisible.value)
            assertEquals(NotRequired, it.permission.value)
        }
    }

    @Test fun requestLaunchesTheSystemDialogAndRemembersIt() = runTest {
        val environment = FakeEnvironment()
        val prefs = FakePushPrefs()
        val controller = NotificationPermissionController(environment, prefs)
        controller.onMainScreenReached()
        controller.requestPermission()
        assertEquals(1, environment.launches)
        assertFalse(controller.promptVisible.value)
        assertTrue(prefs.permissionAsked())
        // Refus sans possibilité de redemander : seuls les réglages du système restent.
        controller.onRequestResult()
        assertEquals(PermanentlyDenied, controller.permission.value)
        controller.requestPermission()
        assertEquals(1, environment.launches)
    }

    @Test fun grantingInTheSystemDialogUpdatesTheState() = runTest {
        val environment = FakeEnvironment()
        val controller = NotificationPermissionController(environment, FakePushPrefs())
        controller.requestPermission()
        environment.granted = true
        controller.onRequestResult()
        assertEquals(Granted, controller.permission.value)
    }

    @Test fun enablingInSystemSettingsIsPickedUpOnRefresh() = runTest {
        val environment = FakeEnvironment()
        val prefs = FakePushPrefs().also { it.setPermissionAsked() }
        val controller = NotificationPermissionController(environment, prefs)
        controller.refresh()
        assertEquals(PermanentlyDenied, controller.permission.value)
        environment.granted = true
        controller.refresh()
        assertEquals(Granted, controller.permission.value)
    }

    // endregion
}
