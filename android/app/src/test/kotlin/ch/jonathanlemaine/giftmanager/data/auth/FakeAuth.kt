package ch.jonathanlemaine.giftmanager.data.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Contexte factice : les fakes ne s'en servent pas. */
fun fakeContext(): Context = ContextWrapper(null)

class FakeAuthRepository(initial: AuthState = AuthState.SignedOut) : AuthRepository {
    val stateFlow = MutableStateFlow(initial)
    override val state: Flow<AuthState> = stateFlow

    var signInFailure: Throwable? = null
    var signInGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var signInCalls = 0
    var signOutCalls = 0
    var signOutFailure: Throwable? = null

    override suspend fun signInWithGoogle(activityContext: Context) {
        signInCalls++
        signInGate?.await()
        signInFailure?.let { throw it }
    }

    var appleCalls = 0
    var appleFailure: Throwable? = null
    var appleGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val callbackFailureFlow = MutableSharedFlow<AuthError>(extraBufferCapacity = 1)
    override val callbackFailures: Flow<AuthError> = callbackFailureFlow

    override suspend fun signInWithApple() {
        appleCalls++
        appleGate?.await()
        appleFailure?.let { throw it }
    }

    override fun handleCallback(intent: Intent) = Unit

    override suspend fun signOut() {
        signOutFailure?.let { throw it }
        signOutCalls++
        stateFlow.value = AuthState.SignedOut
    }
}
