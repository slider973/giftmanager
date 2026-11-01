package ch.jonathanlemaine.giftmanager.data.auth

import android.content.Context
import ch.jonathanlemaine.giftmanager.core.config.AppConfig
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.UUID

class AuthRepositoryTest {
    private class FakeSource(var token: String = "id-token", var failure: Throwable? = null) : GoogleCredentialSource {
        var receivedHash: String? = null

        override suspend fun requestIdToken(activityContext: Context, nonceHash: String): String {
            receivedHash = nonceHash
            failure?.let { throw it }
            return token
        }
    }

    private class FakeBackend(initial: SessionStatus = SessionStatus.Initializing) : AuthBackend {
        val status = MutableStateFlow(initial)
        override val sessionStatus: Flow<SessionStatus> = status
        var failure: Throwable? = null
        var receivedToken: String? = null
        var receivedNonce: String? = null
        var signOutCalls = 0

        override suspend fun signInWithGoogle(idToken: String, nonce: String) {
            receivedToken = idToken
            receivedNonce = nonce
            failure?.let { throw it }
        }

        override suspend fun signOut() {
            signOutCalls++
        }
    }

    private val configured = AppConfig("https://x.supabase.co", "sb_publishable_x", "abc123.apps.googleusercontent.com")

    private fun repository(
        backend: FakeBackend = FakeBackend(),
        source: FakeSource = FakeSource(),
        config: AppConfig = configured,
    ) = SupabaseAuthRepository(backend, config, source)

    @Test fun googleReceivesHashOfTheNonceSupabaseReceives() = runTest {
        val backend = FakeBackend()
        val source = FakeSource()
        repository(backend, source).signInWithGoogle(fakeContext())
        val raw = backend.receivedNonce!!
        assertEquals(sha256Hex(raw), source.receivedHash)
        assertEquals("id-token", backend.receivedToken)
        assertTrue(raw != source.receivedHash)
    }

    @Test fun notConfiguredFailsBeforeAnyGoogleCall() = runTest {
        val source = FakeSource()
        val repo = repository(source = source, config = configured.copy(googleWebClientId = "dummy.apps.googleusercontent.com"))
        try {
            repo.signInWithGoogle(fakeContext())
            fail("NotConfigured attendu")
        } catch (_: AuthError.NotConfigured) {
            assertEquals(null, source.receivedHash)
        }
    }

    @Test fun cancellationFromGooglePropagates() = runTest {
        val backend = FakeBackend()
        try {
            repository(backend, FakeSource(failure = AuthError.Cancelled())).signInWithGoogle(fakeContext())
            fail("Cancelled attendu")
        } catch (_: AuthError.Cancelled) {
            assertEquals(null, backend.receivedToken)
        }
    }

    @Test fun ioFailureBecomesNetwork() = runTest {
        val backend = FakeBackend().apply { failure = IOException("hors ligne") }
        try {
            repository(backend).signInWithGoogle(fakeContext())
            fail("Network attendu")
        } catch (_: AuthError.Network) {
        }
    }

    @Test fun wrappedIoFailureBecomesNetwork() = runTest {
        val backend = FakeBackend().apply { failure = IllegalStateException("enveloppe", IOException("hors ligne")) }
        try {
            repository(backend).signInWithGoogle(fakeContext())
            fail("Network attendu")
        } catch (_: AuthError.Network) {
        }
    }

    @Test fun otherBackendFailureBecomesFailed() = runTest {
        val backend = FakeBackend().apply { failure = IllegalStateException("jeton refusé") }
        try {
            repository(backend).signInWithGoogle(fakeContext())
            fail("Failed attendu")
        } catch (_: AuthError.Failed) {
        }
    }

    @Test fun signOutDelegatesToBackend() = runTest {
        val backend = FakeBackend()
        repository(backend).signOut()
        assertEquals(1, backend.signOutCalls)
    }

    @Test fun stateFollowsSessionStatus() = runTest {
        val id = UUID.randomUUID()
        val session = UserSession(
            accessToken = "a",
            refreshToken = "r",
            expiresIn = 3600,
            tokenType = "bearer",
            user = UserInfo(id = id.toString(), aud = "authenticated"),
        )
        val backend = FakeBackend()
        val states = repository(backend).state
        assertEquals(AuthState.Loading, states.first())
        backend.status.value = SessionStatus.NotAuthenticated(isSignOut = false)
        assertEquals(AuthState.SignedOut, states.first())
        backend.status.value = SessionStatus.Authenticated(session, SessionSource.SignIn(io.github.jan.supabase.auth.providers.Google))
        assertEquals(AuthState.SignedIn(id), states.first())
    }
}
