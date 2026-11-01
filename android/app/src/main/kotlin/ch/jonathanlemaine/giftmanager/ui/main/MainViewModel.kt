package ch.jonathanlemaine.giftmanager.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLock
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    /** Lien partagé à ajouter ; `null` pendant le mode enfant (il attend la sortie) ou sans partage. */
    val pendingSharedLink: StateFlow<String?> = combine(session.pendingSharedLink, childModeLock.active) { link, active ->
        link.takeUnless { active }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, session.pendingSharedLink.value.takeUnless { childModeLock.active.value })

    /** Partage de texte sans lien à annoncer ; `false` pendant le mode enfant. */
    val shareWithoutLink: StateFlow<Boolean> = combine(session.shareWithoutLink, childModeLock.active) { missing, active ->
        missing && !active
    }.stateIn(viewModelScope, SharingStarted.Eagerly, session.shareWithoutLink.value && !childModeLock.active.value)

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

    /** Le lien partagé est pris en charge par l'écran (formulaire, choix de l'enfant ou message). */
    fun consumeSharedLink() = session.clearPendingSharedLink()

    fun consumeShareWithoutLink() = session.clearShareWithoutLink()

    fun dismissError() {
        _error.value = null
    }
}
