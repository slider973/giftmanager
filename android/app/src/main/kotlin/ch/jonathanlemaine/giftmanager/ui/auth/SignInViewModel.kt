package ch.jonathanlemaine.giftmanager.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.data.auth.AuthError
import ch.jonathanlemaine.giftmanager.data.auth.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SignInViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.Idle)
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    /**
     * Lance la connexion Google. [activityContext] (l'Activity) ne sert qu'à afficher le sélecteur
     * de compte et n'est pas conservé. Ignoré si une connexion est déjà en cours.
     * En cas de succès, le changement de session est publié par `AuthRepository.state`.
     */
    fun signIn(activityContext: Context) {
        if (_uiState.value is SignInUiState.InProgress) return
        _uiState.value = SignInUiState.InProgress
        viewModelScope.launch {
            _uiState.value = try {
                auth.signInWithGoogle(activityContext)
                SignInUiState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError.Cancelled) {
                SignInUiState.Idle
            } catch (e: AuthError) {
                SignInUiState.Error(e.toKind())
            } catch (_: Exception) {
                SignInUiState.Error(SignInUiState.Kind.Generic)
            }
        }
    }

    /** Affiche l'échec d'une connexion hors Google (comptes de démo en debug) : [detail] ou le message générique. */
    fun showError(detail: String?) {
        _uiState.value = SignInUiState.Error(SignInUiState.Kind.Generic, detail)
    }

    /** Ferme le message d'erreur affiché. */
    fun dismissMessage() {
        if (_uiState.value is SignInUiState.Error) _uiState.value = SignInUiState.Idle
    }
}

private fun AuthError.toKind(): SignInUiState.Kind = when (this) {
    is AuthError.NoGoogleAccount -> SignInUiState.Kind.NoGoogleAccount
    is AuthError.Network -> SignInUiState.Kind.Network
    is AuthError.NotConfigured -> SignInUiState.Kind.NotConfigured
    is AuthError.GoogleUnavailable -> SignInUiState.Kind.GoogleUnavailable
    is AuthError.Cancelled, is AuthError.Failed -> SignInUiState.Kind.Generic
}
