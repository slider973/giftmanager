package ch.jonathanlemaine.giftmanager.data.auth

import android.content.Context
import android.content.Intent
import ch.jonathanlemaine.giftmanager.core.config.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

/** État de la session, tel que le routage racine le consomme. */
sealed interface AuthState {
    /** Restauration de la session stockée en cours. */
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val userId: UUID) : AuthState
}

interface AuthRepository {
    val state: Flow<AuthState>

    /**
     * Connexion via le sélecteur de compte Google. [activityContext] n'est pas conservé.
     * @throws AuthError (jamais d'autre exception métier).
     */
    suspend fun signInWithGoogle(activityContext: Context)

    /**
     * Connexion Apple par le web (#83) : ouvre la page Apple dans un Custom Tab puis rend la main aussitôt ;
     * la session arrive par [handleCallback] au retour du navigateur, puis par [state].
     * @throws AuthError (jamais d'autre exception métier).
     */
    suspend fun signInWithApple()

    /** Traite l'intent de retour `giftmanager://login-callback` (échange du code contre une session). */
    fun handleCallback(intent: Intent)

    /** Échecs survenus au retour du navigateur, après que [signInWithApple] a déjà rendu la main. */
    val callbackFailures: Flow<AuthError>

    suspend fun signOut()
}

/** Ce que [SupabaseAuthRepository] attend du client Supabase : isolé pour être testable sans réseau. */
interface AuthBackend {
    val sessionStatus: Flow<SessionStatus>

    /** Échange un jeton d'identité Google (et son nonce brut) contre une session. */
    suspend fun signInWithGoogle(idToken: String, nonce: String)

    /** Ouvre la page de connexion Apple dans le navigateur (retour par deep link). */
    suspend fun signInWithApple()

    /** Échange le code reçu dans [intent] ; [onError] est appelé en cas d'échec. */
    fun handleCallback(intent: Intent, onError: (Throwable) -> Unit)

    suspend fun signOut()
}

class SupabaseAuthBackend(private val client: SupabaseClient) : AuthBackend {
    override val sessionStatus: Flow<SessionStatus> get() = client.auth.sessionStatus

    override suspend fun signInWithGoogle(idToken: String, nonce: String) {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            this.nonce = nonce
        }
    }

    override suspend fun signInWithApple() {
        client.auth.signInWith(Apple)
    }

    override fun handleCallback(intent: Intent, onError: (Throwable) -> Unit) {
        client.handleDeeplinks(intent, onSessionSuccess = {}, onError = onError)
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }
}

class SupabaseAuthRepository(
    private val backend: AuthBackend,
    private val config: AppConfig,
    private val googleSource: GoogleCredentialSource,
) : AuthRepository {
    override val state: Flow<AuthState> = backend.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Initializing -> AuthState.Loading
                is SessionStatus.Authenticated -> status.session.user?.id
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?.let(AuthState::SignedIn)
                    ?: AuthState.SignedOut
                // NotAuthenticated, ou rafraîchissement impossible (réseau) : retour à l'écran de connexion.
                else -> AuthState.SignedOut
            }
        }
        .distinctUntilChanged()

    override suspend fun signInWithGoogle(activityContext: Context) {
        if (!config.isGoogleConfigured) throw AuthError.NotConfigured()
        // Le nonce brut ne quitte pas cette fonction : Google reçoit son hash, Supabase le brut.
        val rawNonce = randomNonce()
        val idToken = googleSource.requestIdToken(activityContext, sha256Hex(rawNonce))
        try {
            backend.signInWithGoogle(idToken, rawNonce)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthError) {
            throw e
        } catch (e: Exception) {
            throw if (e.isNetworkFailure()) AuthError.Network(e) else AuthError.Failed(e)
        }
    }

    override suspend fun signInWithApple() {
        if (!config.appleSignInEnabled) throw AuthError.AppleNotConfigured()
        try {
            backend.signInWithApple()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthError) {
            throw e
        } catch (e: Exception) {
            throw if (e.isNetworkFailure()) AuthError.Network(e) else AuthError.Failed(e)
        }
    }

    private val _callbackFailures = MutableSharedFlow<AuthError>(extraBufferCapacity = 1)
    override val callbackFailures: Flow<AuthError> = _callbackFailures

    override fun handleCallback(intent: Intent) {
        backend.handleCallback(intent) { e ->
            _callbackFailures.tryEmit(if (e.isNetworkFailure()) AuthError.Network(e) else AuthError.Failed(e))
        }
    }

    override suspend fun signOut() {
        backend.signOut()
    }
}

private fun Throwable.isNetworkFailure(): Boolean =
    generateSequence(this) { it.cause }.any { it is IOException || it is HttpRequestException }
