package ch.jonathanlemaine.giftmanager.ui.home

/** État de session affiché sur l'écran d'accueil. */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data object SignedOut : HomeUiState

    data class SignedIn(val displayName: String) : HomeUiState

    data object Error : HomeUiState
}
