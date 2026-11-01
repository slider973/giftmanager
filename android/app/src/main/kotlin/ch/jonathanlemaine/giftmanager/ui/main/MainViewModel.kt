package ch.jonathanlemaine.giftmanager.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLock
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Écran principal : confirmation d'une invitation reçue par lien pendant l'usage de l'app. */
class MainViewModel(private val session: AppSession, childModeLock: ChildModeLock) : ViewModel() {
    /** Code à confirmer (« Rejoindre une famille ? »), `null` sans invitation en attente. */
    val pendingInviteCode: StateFlow<String?> = session.pendingInviteCode

    /**
     * Mode enfant en cours : l'écran n'affiche alors ni l'invitation ni l'erreur globale (ils restent en attente et
     * s'affichent à la sortie).
     */
    val childModeActive: StateFlow<Boolean> = childModeLock.active

    private val _error = MutableStateFlow<GiftError?>(null)
    val error: StateFlow<GiftError?> = _error.asStateFlow()

    fun confirmInvite() {
        val code = session.pendingInviteCode.value ?: return
        session.clearPendingInviteCode()
        viewModelScope.launch {
            // Rejoindre puis tout recharger : le foyer éventuel est ensuite à partager ou à créer.
            val result = session.joinGroup(code).mapCatching { session.refreshAll().getOrThrow() }
            _error.value = result.exceptionOrNull()?.let(GiftError::from)
        }
    }

    fun dismissInvite() = session.clearPendingInviteCode()

    fun dismissError() {
        _error.value = null
    }
}
