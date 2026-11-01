package ch.jonathanlemaine.giftmanager.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.core.model.Household
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.InviteLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * État du champ « Nom de la famille » (iOS : `RenameGroupState`). Il part toujours de la famille affichée,
 * pour qu'un changement de famille ne laisse jamais l'ancien nom (ni le bouton « Renommer ») un instant.
 */
data class RenameGroupState(val originalName: String, val name: String) {
    constructor(group: FamilyGroup) : this(group.name, group.name)

    /** Vrai seulement si l'utilisateur a réellement saisi un autre nom. */
    val canRename: Boolean
        get() {
            val trimmed = name.trim()
            return trimmed.isNotEmpty() && trimmed != originalName
        }
}

/** Un foyer de la famille, avec ses parents, prêt à afficher. */
data class HouseholdItem(
    val household: Household,
    val isMine: Boolean,
    val parentNames: List<String>,
)

data class FamilyHomeUiState(
    /** Onglet : 0 Événements, 1 Membres, 2 Paramètres. */
    val tab: Int = 0,
    val group: FamilyGroup? = null,
    val memberNames: List<String> = emptyList(),
    val memberCount: Int = 0,
    val householdCount: Int = 0,
    /** Mon foyer en premier. */
    val households: List<HouseholdItem> = emptyList(),
    val groups: List<FamilyGroup> = emptyList(),
    val rename: RenameGroupState? = null,
    val inviteMessage: String? = null,
    val showJoinDialog: Boolean = false,
    val joinCode: String = "",
    val isRefreshing: Boolean = false,
    val isBusy: Boolean = false,
    val error: GiftError? = null,
) {
    val canJoin: Boolean get() = joinCode.trim().length >= GroupSetupUiState.GROUP_CODE_LENGTH && !isBusy
}

/** « Notre famille » : membres et paramètres (iOS : `FamilyHomeView`) ; les événements arrivent avec #72. */
class FamilyHomeViewModel(private val session: AppSession) : ViewModel() {
    private data class Local(
        val tab: Int = 0,
        val renameEdit: Pair<UUID, String>? = null,
        val showJoinDialog: Boolean = false,
        val joinCode: String = "",
        val isRefreshing: Boolean = false,
        val isBusy: Boolean = false,
        val error: GiftError? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<FamilyHomeUiState> = combine(local, session.state) { l, s ->
        val group = s.currentGroup
        val mine = s.myHousehold
        val items = s.households.map { HouseholdItem(it, it.id == mine?.id, s.parents(it).map { p -> p.displayName }) }
        FamilyHomeUiState(
            tab = l.tab,
            group = group,
            memberNames = s.members.map { it.displayName },
            memberCount = s.members.size,
            householdCount = s.households.size,
            households = items.sortedByDescending { it.isMine },
            groups = s.groups,
            // Recréé à chaque changement de famille : une saisie n'est reprise que pour la même famille.
            rename = group?.let { g ->
                val edit = l.renameEdit?.takeIf { it.first == g.id }
                RenameGroupState(g).let { base -> if (edit != null) base.copy(name = edit.second) else base }
            },
            inviteMessage = group?.let(InviteLink::message),
            showJoinDialog = l.showJoinDialog,
            joinCode = l.joinCode,
            isRefreshing = l.isRefreshing,
            isBusy = l.isBusy,
            error = l.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FamilyHomeUiState())

    fun selectTab(tab: Int) = local.update { it.copy(tab = tab.coerceIn(0, 2)) }

    fun onRenameChange(name: String) {
        val group = uiState.value.group ?: return
        local.update { it.copy(renameEdit = group.id to name) }
    }

    fun rename() {
        val state = uiState.value
        val group = state.group ?: return
        val rename = state.rename ?: return
        if (!rename.canRename || state.isBusy) return
        perform { session.renameGroup(group.id, rename.name.trim()) }
    }

    fun selectGroup(group: FamilyGroup) {
        if (group.id == uiState.value.group?.id) return
        perform { session.selectGroup(group) }
    }

    fun refresh() {
        local.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val failure = session.refreshGroupData().exceptionOrNull()
            local.update { it.copy(isRefreshing = false, error = failure?.let(GiftError::from) ?: it.error) }
        }
    }

    fun showJoinDialog() = local.update { it.copy(showJoinDialog = true, joinCode = "") }
    fun hideJoinDialog() = local.update { it.copy(showJoinDialog = false) }
    fun onJoinCodeChange(code: String) = local.update { it.copy(joinCode = code.uppercase()) }

    /** Rejoint une autre famille avec un code, puis recharge pour que la phase suive (foyer à créer ou partager). */
    fun joinOtherGroup() {
        val state = uiState.value
        if (!state.canJoin) return
        local.update { it.copy(showJoinDialog = false) }
        perform {
            session.joinGroup(state.joinCode.trim()).mapCatching { session.refreshAll().getOrThrow() }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }

    private fun perform(action: suspend () -> Result<Unit>) {
        local.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val failure = action().exceptionOrNull()
            local.update { it.copy(isBusy = false, error = failure?.let(GiftError::from)) }
        }
    }
}
