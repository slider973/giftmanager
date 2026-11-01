package ch.jonathanlemaine.giftmanager.core.childmode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Verrou du mode enfant, partagé par toute l'app (singleton de `AppContainer`). Tant qu'il est actif, rien ne doit
 * s'afficher par-dessus l'écran : ni invitation à rejoindre une famille, ni erreur globale, ni (plus tard) une
 * notification ouverte (iOS : `NotificationRouter.suspended`).
 */
class ChildModeLock {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun activate() {
        _active.value = true
    }

    fun release() {
        _active.value = false
    }
}
