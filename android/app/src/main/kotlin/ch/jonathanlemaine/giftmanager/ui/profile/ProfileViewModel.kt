package ch.jonathanlemaine.giftmanager.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.data.prefs.LocalPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    /** Nom enregistré (en-tête) et nom en cours de saisie. */
    val displayName: String = "",
    val name: String = "",
    val country: String = Countries.DEFAULT_COUNTRY,
    val currency: String = "CHF",
    val hasChanges: Boolean = false,
    val isSaving: Boolean = false,
    val householdName: String? = null,
    /** Familles où mon foyer est visible ; « Retirer » n'est proposé que s'il y en a plusieurs. */
    val sharedFamilies: List<FamilyGroup> = emptyList(),
    val purchaseReminders: Boolean = true,
    val birthdayReminders: Boolean = true,
    /** Préférence locale : masque les notifications « Nouvelles envies » à leur arrivée (#76). */
    val familyNews: Boolean = true,
    /** Famille dont on s'apprête à retirer le partage du foyer (confirmation affichée). */
    val unsharing: FamilyGroup? = null,
    val confirmingDelete: Boolean = false,
    val error: GiftError? = null,
) {
    val canSave: Boolean get() = hasChanges && name.trim().isNotEmpty() && !isSaving
    val canUnshare: Boolean get() = sharedFamilies.size > 1
}

/** Profil : identité, foyer, notifications, déconnexion et suppression de compte (iOS : `ProfileView`). */
class ProfileViewModel(private val session: AppSession, private val prefs: LocalPrefs) : ViewModel() {
    private data class Local(
        val name: String,
        val country: String,
        val currency: String,
        val isSaving: Boolean = false,
        /** Valeurs affichées avant la réponse du serveur ; retirées au retour (réussite ou échec = retour arrière). */
        val birthdayOverride: Boolean? = null,
        val purchaseOverride: Boolean? = null,
        val unsharing: FamilyGroup? = null,
        val confirmingDelete: Boolean = false,
        val error: GiftError? = null,
    )

    private val local: MutableStateFlow<Local>

    init {
        val profile = session.state.value.profile
        local = MutableStateFlow(
            Local(
                name = profile?.displayName.orEmpty(),
                country = profile?.country ?: Countries.DEFAULT_COUNTRY,
                currency = profile?.currency ?: "CHF",
            ),
        )
    }

    val uiState: StateFlow<ProfileUiState> = combine(local, session.state, prefs.notifyFamilyNews) { l, s, familyNews ->
        val profile = s.profile
        ProfileUiState(
            displayName = profile?.displayName.orEmpty(),
            name = l.name,
            country = l.country,
            currency = l.currency,
            hasChanges = profile != null &&
                (l.name.trim() != profile.displayName || l.country != profile.country || l.currency != profile.currency),
            isSaving = l.isSaving,
            householdName = s.myHousehold?.name,
            sharedFamilies = s.familiesSharingMyHousehold,
            purchaseReminders = l.purchaseOverride ?: profile?.notifyPurchaseReminders ?: true,
            birthdayReminders = l.birthdayOverride ?: profile?.notifyBirthdayReminders ?: true,
            familyNews = familyNews,
            unsharing = l.unsharing,
            confirmingDelete = l.confirmingDelete,
            error = l.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    fun onNameChange(name: String) = local.update { it.copy(name = name) }

    /** La devise suggérée suit le pays, uniquement quand l'utilisateur change de pays. */
    fun onCountryChange(country: String) =
        local.update { it.copy(country = country, currency = Countries.currencyFor(country) ?: it.currency) }

    fun onCurrencyChange(currency: String) = local.update { it.copy(currency = currency) }

    fun save() {
        val state = uiState.value
        if (!state.canSave) return
        local.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val failure = session.saveProfile(state.name.trim(), state.country, state.currency).exceptionOrNull()
            local.update { it.copy(isSaving = false, name = state.name.trim(), error = failure?.let(GiftError::from)) }
        }
    }

    /** Bascule immédiate ; si le serveur échoue, l'interrupteur revient à l'état enregistré. */
    fun setBirthdayReminders(enabled: Boolean) {
        local.update { it.copy(birthdayOverride = enabled) }
        viewModelScope.launch {
            val failure = session.setBirthdayReminders(enabled).exceptionOrNull()
            local.update { it.copy(birthdayOverride = null, error = failure?.let(GiftError::from) ?: it.error) }
        }
    }

    fun setPurchaseReminders(enabled: Boolean) {
        local.update { it.copy(purchaseOverride = enabled) }
        viewModelScope.launch {
            val failure = session.setPurchaseReminders(enabled).exceptionOrNull()
            local.update { it.copy(purchaseOverride = null, error = failure?.let(GiftError::from) ?: it.error) }
        }
    }

    fun setFamilyNews(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotifyFamilyNews(enabled) }
    }

    fun requestUnshare(group: FamilyGroup) = local.update { it.copy(unsharing = group) }
    fun cancelUnshare() = local.update { it.copy(unsharing = null) }

    fun confirmUnshare() {
        val group = local.value.unsharing ?: return
        local.update { it.copy(unsharing = null) }
        viewModelScope.launch {
            val failure = session.unshareHousehold(group).exceptionOrNull()
            if (failure != null) local.update { it.copy(error = GiftError.from(failure)) }
        }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    fun requestDelete() = local.update { it.copy(confirmingDelete = true) }
    fun cancelDelete() = local.update { it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        local.update { it.copy(confirmingDelete = false) }
        viewModelScope.launch {
            val failure = session.deleteAccount().exceptionOrNull()
            if (failure != null) local.update { it.copy(error = GiftError.from(failure)) }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }
}
