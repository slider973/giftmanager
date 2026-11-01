package ch.jonathanlemaine.giftmanager.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class ProfileSetupUiState(
    val name: String = "",
    val country: String = Countries.DEFAULT_COUNTRY,
    val currency: String = "CHF",
    val isSaving: Boolean = false,
    val error: GiftError? = null,
) {
    val canSave: Boolean get() = name.trim().isNotEmpty() && !isSaving
}

/** Premier lancement : prénom, pays et devise (iOS : `ProfileSetupView`). */
class ProfileSetupViewModel(
    private val session: AppSession,
    locale: Locale = Locale.getDefault(),
) : ViewModel() {
    private val _uiState: MutableStateFlow<ProfileSetupUiState>
    val uiState: StateFlow<ProfileSetupUiState>

    init {
        val profile = session.state.value.profile
        // Un profil déjà renseigné (retour en arrière) prime ; sinon la région de l'appareil, si elle est proposée.
        val country = profile?.takeIf { it.onboarded }?.country ?: Countries.defaultCountry(locale)
        val currency = profile?.takeIf { it.onboarded }?.currency ?: Countries.currencyFor(country) ?: "CHF"
        _uiState = MutableStateFlow(ProfileSetupUiState(name = profile?.displayName.orEmpty(), country = country, currency = currency))
        uiState = _uiState.asStateFlow()
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    /** La devise suggérée suit le pays, uniquement quand l'utilisateur change de pays. */
    fun onCountryChange(country: String) =
        _uiState.update { it.copy(country = country, currency = Countries.currencyFor(country) ?: it.currency) }

    fun onCurrencyChange(currency: String) = _uiState.update { it.copy(currency = currency) }

    fun save() {
        val current = _uiState.value
        if (!current.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = session.saveProfile(current.name.trim(), current.country, current.currency)
            // Succès : la phase change et cet écran quitte la composition.
            _uiState.update { it.copy(isSaving = false, error = result.exceptionOrNull()?.let(GiftError::from)) }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
