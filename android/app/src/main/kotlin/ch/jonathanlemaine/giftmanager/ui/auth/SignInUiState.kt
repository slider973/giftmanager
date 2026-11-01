package ch.jonathanlemaine.giftmanager.ui.auth

/** État de l'écran de connexion. L'annulation par l'utilisateur ramène silencieusement à [Idle]. */
sealed interface SignInUiState {
    data object Idle : SignInUiState

    data object InProgress : SignInUiState

    data class Error(val kind: Kind, val detail: String? = null) : SignInUiState

    enum class Kind { NoGoogleAccount, Network, NotConfigured, GoogleUnavailable, Generic, AppleNotConfigured, AppleFailed }
}
