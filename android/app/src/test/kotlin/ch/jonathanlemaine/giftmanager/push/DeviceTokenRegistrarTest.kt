package ch.jonathanlemaine.giftmanager.push

import ch.jonathanlemaine.giftmanager.data.DeviceTokenBackend
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceTokenRegistrarTest {
    private class FakeTokens(var token: String? = "token-a") : PushTokenSource {
        var deleted = 0
        override suspend fun currentToken() = token
        override suspend fun deleteToken() {
            deleted++
            token = null
        }
    }

    private class FakeBackend : DeviceTokenBackend {
        val registered = mutableListOf<String>()
        val unregistered = mutableListOf<String>()
        var registerFailure: Throwable? = null
        var unregisterFailure: Throwable? = null

        override suspend fun register(token: String) {
            registerFailure?.let { throw it }
            registered += token
        }

        override suspend fun unregister(token: String) {
            unregisterFailure?.let { throw it }
            unregistered += token
        }
    }

    private val me = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val other = UUID.fromString("00000000-0000-4000-8000-000000000002")

    private class Fixture(scope: TestScope, auth: AuthState, permission: NotificationPermission) {
        val auth = MutableStateFlow(auth)
        val permission = MutableStateFlow(permission)
        val tokens = FakeTokens()
        val backend = FakeBackend()
        val prefs = FakePushPrefs()
        val registrar = DeviceTokenRegistrar(this.auth, this.permission, tokens, backend, prefs).also { it.start(scope.backgroundScope) }
    }

    private fun TestScope.fixture(
        auth: AuthState = AuthState.SignedIn(me),
        permission: NotificationPermission = NotificationPermission.Granted,
    ) = Fixture(this, auth, permission).also { runCurrent() }

    @Test fun registersOnceWhenSignedInAndAllowed() = runTest {
        val f = fixture()
        assertEquals(listOf("token-a"), f.backend.registered)
        f.registrar.sync()
        f.registrar.sync()
        assertEquals(listOf("token-a"), f.backend.registered)
        assertEquals(RegisteredToken(me, "token-a"), f.prefs.registered)
    }

    @Test fun nothingWhenSignedOut() = runTest {
        val f = fixture(auth = AuthState.SignedOut)
        f.registrar.sync()
        f.registrar.onNewToken("token-b")
        assertEquals(emptyList<String>(), f.backend.registered)
    }

    @Test fun nothingWhileLoading() = runTest {
        val f = fixture(auth = AuthState.Loading)
        assertEquals(emptyList<String>(), f.backend.registered)
    }

    @Test fun nothingBeforeThePermissionIsGranted() = runTest {
        val f = fixture(permission = NotificationPermission.CanAsk)
        f.registrar.sync()
        assertEquals(emptyList<String>(), f.backend.registered)
        f.permission.value = NotificationPermission.Granted
        runCurrent()
        assertEquals(listOf("token-a"), f.backend.registered)
    }

    @Test fun registersDirectlyWhenNoRuntimePermissionIsNeeded() = runTest {
        val f = fixture(permission = NotificationPermission.NotRequired)
        assertEquals(listOf("token-a"), f.backend.registered)
    }

    @Test fun permanentlyDeniedDoesNotRegister() = runTest {
        val f = fixture(permission = NotificationPermission.PermanentlyDenied)
        assertEquals(emptyList<String>(), f.backend.registered)
    }

    @Test fun registersAfterSigningIn() = runTest {
        val f = fixture(auth = AuthState.SignedOut)
        f.auth.value = AuthState.SignedIn(me)
        runCurrent()
        assertEquals(listOf("token-a"), f.backend.registered)
    }

    @Test fun anotherAccountOnTheSameDeviceRegistersAgain() = runTest {
        val f = fixture()
        f.auth.value = AuthState.SignedIn(other)
        runCurrent()
        assertEquals(listOf("token-a", "token-a"), f.backend.registered)
        assertEquals(RegisteredToken(other, "token-a"), f.prefs.registered)
    }

    @Test fun refreshedTokenIsRegistered() = runTest {
        val f = fixture()
        f.registrar.onNewToken("token-b")
        assertEquals(listOf("token-a", "token-b"), f.backend.registered)
        assertEquals(RegisteredToken(me, "token-b"), f.prefs.registered)
    }

    @Test fun silentFailureIsRetriedAtTheNextSync() = runTest {
        val f = Fixture(this, AuthState.SignedIn(me), NotificationPermission.Granted)
        f.backend.registerFailure = IOException("hors ligne")
        runCurrent()
        assertEquals(emptyList<String>(), f.backend.registered)
        assertNull(f.prefs.registered)
        f.backend.registerFailure = null
        f.registrar.sync()
        assertEquals(listOf("token-a"), f.backend.registered)
        assertEquals(RegisteredToken(me, "token-a"), f.prefs.registered)
    }

    @Test fun missingFirebaseTokenIsIgnored() = runTest {
        val f = Fixture(this, AuthState.SignedIn(me), NotificationPermission.Granted)
        f.tokens.token = null
        runCurrent()
        f.registrar.sync()
        assertEquals(emptyList<String>(), f.backend.registered)
    }

    @Test fun signingOutRemovesTheTokenOnTheServerThenAtFirebase() = runTest {
        val f = fixture()
        f.registrar.unregister()
        assertEquals(listOf("token-a"), f.backend.unregistered)
        assertEquals(1, f.tokens.deleted)
        assertNull(f.prefs.registered)
    }

    @Test fun nothingIsRegisteredWhileSigningOut() = runTest {
        val f = fixture()
        f.registrar.unregister()
        // Firebase émet un nouveau jeton après deleteToken, la session n'est pas encore fermée.
        f.registrar.onNewToken("token-b")
        f.registrar.sync()
        assertEquals(listOf("token-a"), f.backend.registered)
    }

    @Test fun signingBackInRegistersAgain() = runTest {
        val f = fixture()
        f.registrar.unregister()
        f.auth.value = AuthState.SignedOut
        runCurrent()
        f.tokens.token = "token-c"
        f.auth.value = AuthState.SignedIn(me)
        runCurrent()
        assertEquals(listOf("token-a", "token-c"), f.backend.registered)
    }

    @Test fun signingOutNeverFailsWhenTheServerIsUnreachable() = runTest {
        val f = fixture()
        f.backend.unregisterFailure = IOException("hors ligne")
        f.registrar.unregister()
        assertEquals(1, f.tokens.deleted)
        assertNull(f.prefs.registered)
    }

    @Test fun signingOutWithoutARegisteredTokenOnlyClearsFirebase() = runTest {
        val f = fixture(permission = NotificationPermission.CanAsk)
        f.registrar.unregister()
        assertEquals(emptyList<String>(), f.backend.unregistered)
        assertEquals(1, f.tokens.deleted)
    }
}
