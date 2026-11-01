package ch.jonathanlemaine.giftmanager.push

import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLock
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Destination d'une notification touchée, en attente d'être ouverte (singleton de `AppContainer`) — iOS :
 * `NotificationRouter.pendingItem`. Elle survit tant que l'écran principal n'existe pas (chargement, connexion,
 * profil et famille à compléter), et ne se déclenche qu'une fois : [consume] la retire.
 *
 * - Une nouvelle notification remplace la précédente non consommée.
 * - Déconnecté, la destination est effacée (elle concernait le compte précédent) : voir [onAuthState].
 * - Pendant le mode enfant, [deliverable] n'émet rien (iOS : `NotificationRouter.suspended`) ; la destination
 *   attend la sortie.
 */
class PendingNavigation(private val childModeLock: ChildModeLock) {
    private val pending = MutableStateFlow<NotificationTarget?>(null)
    private var wasSignedIn = false

    /** La notification dont l'ouverture est demandée (le plus souvent via les extras de `MainActivity`). */
    fun post(target: NotificationTarget) {
        pending.value = target
    }

    /** Destination à ouvrir maintenant : `null` sans notification en attente ou pendant le mode enfant. */
    fun deliverable(): Flow<NotificationTarget?> =
        combine(pending, childModeLock.active) { target, childMode -> target.takeUnless { childMode } }.distinctUntilChanged()

    /** Marque [target] comme ouverte. Sans effet si une autre notification est arrivée entre-temps. */
    fun consume(target: NotificationTarget) {
        pending.compareAndSet(target, null)
    }

    fun clear() {
        pending.value = null
    }

    /**
     * Suit l'authentification : une déconnexion après une session ouverte efface la destination. Un démarrage à froid
     * (Loading puis SignedOut avant d'avoir jamais été connecté) la conserve : elle attend la connexion.
     */
    fun onAuthState(state: AuthState) {
        when (state) {
            is AuthState.SignedIn -> wasSignedIn = true
            AuthState.SignedOut -> {
                if (wasSignedIn) clear()
                wasSignedIn = false
            }
            AuthState.Loading -> Unit
        }
    }
}
