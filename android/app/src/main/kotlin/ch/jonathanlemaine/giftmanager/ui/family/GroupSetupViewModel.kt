package ch.jonathanlemaine.giftmanager.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sous-écran de la configuration de la famille. */
enum class GroupSetupMode {
    /** Créer ou rejoindre une famille. */
    Choose,
    Create,
    Join,

    /** Famille rejointe, foyer à créer, rejoindre ou partager. */
    Household,
}

data class GroupSetupUiState(
    val mode: GroupSetupMode = GroupSetupMode.Choose,
    val groupName: String = "",
    val householdName: String = "",
    val groupCode: String = "",
    val householdCode: String = "",
    /** Nom de la famille rejointe (mode [GroupSetupMode.Household]). */
    val currentGroupName: String = "",
    /** Foyer existant proposé au partage (#60), `null` si rien à partager. */
    val shareableHouseholdName: String? = null,
    val isBusy: Boolean = false,
    val error: GiftError? = null,
) {
    val canCreateGroup: Boolean get() = groupName.trim().isNotEmpty() && householdName.trim().isNotEmpty() && !isBusy
    val canJoinGroup: Boolean get() = groupCode.trim().length >= GROUP_CODE_LENGTH && !isBusy
    val canCreateHousehold: Boolean get() = householdName.trim().isNotEmpty() && !isBusy
    val canJoinHousehold: Boolean get() = householdCode.trim().length >= HOUSEHOLD_CODE_LENGTH && !isBusy

    companion object {
        const val GROUP_CODE_LENGTH = 6
        const val HOUSEHOLD_CODE_LENGTH = 8
    }
}

/** Créer ou rejoindre une famille, puis créer ou rejoindre son foyer (iOS : `GroupSetupView`). */
class GroupSetupViewModel(private val session: AppSession) : ViewModel() {
    private data class Edits(
        val mode: GroupSetupMode = GroupSetupMode.Choose,
        val groupName: String = "",
        val householdName: String = "",
        val groupCode: String = "",
        val householdCode: String = "",
        val isBusy: Boolean = false,
        val error: GiftError? = null,
    )

    private val edits = MutableStateFlow(Edits())

    val uiState: StateFlow<GroupSetupUiState> = combine(edits, session.state) { edit, s ->
        // Dès qu'une famille est rejointe sans foyer, on passe à l'étape foyer quel que soit le mode local.
        val needsHousehold = s.currentGroup != null && s.myHousehold == null
        GroupSetupUiState(
            mode = if (needsHousehold) GroupSetupMode.Household else edit.mode,
            groupName = edit.groupName,
            householdName = edit.householdName,
            groupCode = edit.groupCode,
            householdCode = edit.householdCode,
            currentGroupName = s.currentGroup?.name.orEmpty(),
            // Le foyer existe déjà ailleurs ? Le partager plutôt que d'en créer un second.
            shareableHouseholdName = s.myHousehold?.name?.takeIf { s.canShareHouseholdWithCurrentGroup },
            isBusy = edit.isBusy,
            error = edit.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GroupSetupUiState())

    init {
        val suggested = session.state.value.profile?.displayName?.takeIf { it.isNotBlank() }?.let { "Foyer de $it" }.orEmpty()
        edits.update { it.copy(householdName = suggested) }
        // Lien d'invitation reçu avant ou pendant l'écran : on ouvre « Rejoindre » avec le code.
        viewModelScope.launch {
            session.pendingInviteCode.collect { code ->
                if (code != null) edits.update { it.copy(mode = GroupSetupMode.Join, groupCode = code) }
            }
        }
    }

    fun showChoose() = edits.update { it.copy(mode = GroupSetupMode.Choose, error = null) }
    fun showCreate() = edits.update { it.copy(mode = GroupSetupMode.Create) }
    fun showJoin() = edits.update { it.copy(mode = GroupSetupMode.Join) }

    fun onGroupNameChange(value: String) = edits.update { it.copy(groupName = value) }
    fun onHouseholdNameChange(value: String) = edits.update { it.copy(householdName = value) }
    fun onGroupCodeChange(value: String) = edits.update { it.copy(groupCode = value.uppercase()) }
    fun onHouseholdCodeChange(value: String) = edits.update { it.copy(householdCode = value.uppercase()) }
    fun dismissError() = edits.update { it.copy(error = null) }

    fun createGroup() {
        val s = uiState.value
        if (!s.canCreateGroup) return
        launchAction { session.createGroup(s.groupName.trim(), s.householdName.trim()) }
    }

    fun joinGroup() {
        val s = uiState.value
        if (!s.canJoinGroup) return
        launchAction {
            val result = session.joinGroup(s.groupCode.trim())
            // Le code d'invitation est consommé, réussite ou non (comme iOS).
            session.clearPendingInviteCode()
            result.map { }
        }
    }

    fun createHousehold() {
        val s = uiState.value
        if (!s.canCreateHousehold) return
        launchAction { session.createHousehold(s.householdName.trim()) }
    }

    fun joinHousehold() {
        val s = uiState.value
        if (!s.canJoinHousehold) return
        launchAction { session.joinHousehold(s.householdCode.trim()) }
    }

    fun shareHousehold() {
        if (uiState.value.isBusy) return
        launchAction { session.shareHousehold() }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    private fun launchAction(action: suspend () -> Result<Unit>) {
        edits.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val failure = action().exceptionOrNull()
            edits.update { it.copy(isBusy = false, error = failure?.let(GiftError::from)) }
        }
    }
}
