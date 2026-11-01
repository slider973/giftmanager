package ch.jonathanlemaine.giftmanager.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.data.auth.AuthRepository
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Expose l'état de session qui décide de l'écran racine (chargement, connexion, app). */
class RootViewModel(auth: AuthRepository) : ViewModel() {
    val state: StateFlow<AuthState> =
        auth.state.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)
}
