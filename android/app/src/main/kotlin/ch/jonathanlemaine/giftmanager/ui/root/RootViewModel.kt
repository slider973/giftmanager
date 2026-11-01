package ch.jonathanlemaine.giftmanager.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionPhase
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Écran racine, choisi d'après la phase de la session (iOS : `RootView`). */
enum class RootDestination {
    Loading,

    /** Onboarding puis connexion. */
    Onboarding,

    /** Session valide mais données illisibles au démarrage : réessayer ou se déconnecter. */
    LoadFailed,
    ProfileSetup,
    GroupSetup,
    Main,
}

fun SessionState.toDestination(): RootDestination = when (phase) {
    SessionPhase.Launching -> RootDestination.Loading
    SessionPhase.SignedOut -> if (loadFailed) RootDestination.LoadFailed else RootDestination.Onboarding
    SessionPhase.NeedsProfile -> RootDestination.ProfileSetup
    SessionPhase.NeedsGroup -> RootDestination.GroupSetup
    SessionPhase.Ready -> RootDestination.Main
}

class RootViewModel(private val session: AppSession) : ViewModel() {
    val destination: StateFlow<RootDestination> = session.state
        .map { it.toDestination() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.state.value.toDestination())

    fun retry() {
        viewModelScope.launch { session.refreshAll() }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }
}
