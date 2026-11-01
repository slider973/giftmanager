package ch.jonathanlemaine.giftmanager.push

import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.data.DeviceTokenBackend
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Enregistre le jeton FCM de l'appareil auprès du serveur (iOS : `onDeviceToken` → `register_device`).
 *
 * - Jamais avant l'autorisation des notifications (Android 13+) ni hors session ouverte.
 * - Le dernier jeton envoyé est mémorisé ([PushPrefs]) avec le compte : rien n'est renvoyé tant que ni l'un ni
 *   l'autre ne change ; un autre compte sur le même appareil réenregistre le jeton (le serveur le réattribue).
 * - Un échec est silencieux et rejoué au prochain déclencheur : changement de session ou de permission, nouveau
 *   jeton, retour au premier plan ([sync]).
 * - À la déconnexion, [unregister] retire le jeton du serveur puis chez Firebase, avant la fin de la session.
 */
class DeviceTokenRegistrar(
    private val auth: Flow<AuthState>,
    private val permission: Flow<NotificationPermission>,
    private val tokens: PushTokenSource,
    private val backend: DeviceTokenBackend,
    private val prefs: PushPrefs,
) {
    private val lock = Mutex()
    private var userId: UUID? = null
    private var allowed = false
    private var signingOut = false

    /** Suit la session et la permission : chaque changement relance [sync]. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(auth, permission) { state, permission -> (state as? AuthState.SignedIn)?.userId to permission.canRegister }
                .distinctUntilChanged()
                .collect { (user, canRegister) ->
                    lock.withLock {
                        userId = user
                        allowed = canRegister
                        signingOut = false
                    }
                    sync()
                }
        }
    }

    /** Enregistre le jeton courant s'il ne l'est pas déjà. Sans effet hors session, sans autorisation ou sans Firebase. */
    suspend fun sync() {
        lock.withLock {
            if (!canRegisterNow()) return
            val token = runCatchingCancellable { tokens.currentToken() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
            register(token)
        }
    }

    /** Firebase a renouvelé le jeton (`onNewToken`). */
    suspend fun onNewToken(token: String) {
        lock.withLock {
            if (!canRegisterNow() || token.isBlank()) return
            register(token)
        }
    }

    /**
     * Déconnexion : supprime le jeton côté serveur (tant que la session est valide) puis chez Firebase, pour que
     * l'appareil ne reçoive plus les notifications de ce compte. Silencieux et borné dans le temps : une panne
     * réseau ne doit jamais empêcher de se déconnecter.
     */
    suspend fun unregister() {
        lock.withLock {
            signingOut = true
            val registered = prefs.registeredToken()
            if (registered != null) {
                runCatchingCancellable { withTimeoutOrNull(NETWORK_TIMEOUT_MS) { backend.unregister(registered.token) } }
                runCatchingCancellable { prefs.setRegisteredToken(null) }
            }
            runCatchingCancellable { withTimeoutOrNull(NETWORK_TIMEOUT_MS) { tokens.deleteToken() } }
        }
    }

    private fun canRegisterNow() = userId != null && allowed && !signingOut

    // Appelé sous le verrou, avec une session ouverte.
    private suspend fun register(token: String) {
        val user = userId ?: return
        val entry = RegisteredToken(user, token)
        if (runCatchingCancellable { prefs.registeredToken() }.getOrNull() == entry) return
        val sent = runCatchingCancellable { withTimeoutOrNull(NETWORK_TIMEOUT_MS) { backend.register(token) } }
        // Échec ou délai dépassé : rien n'est mémorisé, le prochain déclencheur réessaie.
        if (sent.getOrNull() != null) runCatchingCancellable { prefs.setRegisteredToken(entry) }
    }

    private companion object {
        const val NETWORK_TIMEOUT_MS = 8_000L
    }
}
