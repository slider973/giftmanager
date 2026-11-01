package ch.jonathanlemaine.giftmanager.data.auth

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.flow.Flow
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

    override suspend fun signOut() {
        signOutFailure?.let { throw it }
        signOutCalls++
        stateFlow.value = AuthState.SignedOut
    }
}
