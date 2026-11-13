package ch.jonathanlemaine.giftmanager.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.Household
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.InviteLink
import ch.jonathanlemaine.giftmanager.core.session.canEdit
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.core.session.children
import ch.jonathanlemaine.giftmanager.core.session.currentEvent
import ch.jonathanlemaine.giftmanager.core.session.pastEvents
import ch.jonathanlemaine.giftmanager.core.session.upcomingEvents
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    /** Enfants puis listes d'adultes du foyer. */
    val children: List<Child> = emptyList(),
)

/** Un événement de la liste, avec ce que la ligne affiche (iOS : `eventRow`). */
data class EventItem(
    val event: GiftEvent,
    /** Prénoms des listes concernées (avatars de la ligne). */
    val avatarNames: List<String>,
    val isPast: Boolean,
    val daysRemaining: Int,
    /** Âge fêté, pour un anniversaire dont on connaît la date de naissance. */
    val age: Int?,
    /** Vrai pour un événement de type « autre » à venir : seul cas où Android propose « Modifier ». */
    val canEdit: Boolean,
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
    val upcomingEvents: List<EventItem> = emptyList(),
    /** Du plus récent au plus ancien. */
    val pastEvents: List<EventItem> = emptyList(),
    /** Événement ouvert avec une liste depuis « Membres » : le prochain Noël, sinon le prochain événement. */
    val currentEventId: java.util.UUID? = null,
    val rename: RenameGroupState? = null,
    val inviteMessage: String? = null,
    val showJoinDialog: Boolean = false,
    val joinCode: String = "",
    /** Idées proposées pour mes enfants et en attente de mon verdict (#57) : bandeau « N idées attendent ton avis ». */
    val pendingIdeasCount: Int = 0,
    val isRefreshing: Boolean = false,
    val isBusy: Boolean = false,
    val error: GiftError? = null,
    /** Famille dont on confirme le départ (#121), `null` sinon. */
    val leaving: FamilyGroup? = null,
) {
    /** Quitter n'est proposé qu'avec une autre famille où revenir (#121). */
    val canLeave: Boolean get() = groups.size > 1
    val canJoin: Boolean get() = joinCode.trim().length >= GroupSetupUiState.GROUP_CODE_LENGTH && !isBusy
}

/** « Notre famille » : événements, membres et paramètres (iOS : `FamilyHomeView`). */
class FamilyHomeViewModel(
    private val session: AppSession,
    private val items: ItemRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private data class Local(
        val tab: Int = 0,
        val renameEdit: Pair<UUID, String>? = null,
        val showJoinDialog: Boolean = false,
        val joinCode: String = "",
        val pendingIdeasCount: Int = 0,
        val isRefreshing: Boolean = false,
        val isBusy: Boolean = false,
        val error: GiftError? = null,
        val leaving: FamilyGroup? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<FamilyHomeUiState> = combine(local, session.state) { l, s ->
        val group = s.currentGroup
        val mine = s.myHousehold
        val items = s.households.map {
            HouseholdItem(it, it.id == mine?.id, s.parents(it).map { p -> p.displayName }, s.children(it))
        }
        fun item(event: GiftEvent): EventItem {
            val isPast = event.isPast(clock)
            val age = if (event.kind == GiftEventKind.BIRTHDAY) s.child(event.childId)?.age(on = event.eventDate.date) else null
            return EventItem(
                event = event,
                avatarNames = s.children(event).map { it.firstName },
                isPast = isPast,
                daysRemaining = event.daysRemaining(clock),
                age = age,
                canEdit = s.canEdit(event, clock),
            )
        }
        FamilyHomeUiState(
            tab = l.tab,
            group = group,
            memberNames = s.members.map { it.displayName },
            memberCount = s.members.size,
            householdCount = s.households.size,
            households = items.sortedByDescending { it.isMine },
            groups = s.groups,
            upcomingEvents = s.upcomingEvents(clock).map(::item),
            pastEvents = s.pastEvents(clock).map(::item),
            currentEventId = s.currentEvent(clock)?.id,
            // Recréé à chaque changement de famille : une saisie n'est reprise que pour la même famille.
            rename = group?.let { g ->
                val edit = l.renameEdit?.takeIf { it.first == g.id }
                RenameGroupState(g).let { base -> if (edit != null) base.copy(name = edit.second) else base }
            },
            inviteMessage = group?.let(InviteLink::message),
            showJoinDialog = l.showJoinDialog,
            joinCode = l.joinCode,
            pendingIdeasCount = l.pendingIdeasCount,
            isRefreshing = l.isRefreshing,
            isBusy = l.isBusy,
            error = l.error,
            leaving = l.leaving,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FamilyHomeUiState())

    init {
        // Au départ, puis à chaque changement de cadeaux (un verdict en est un).
        viewModelScope.launch { session.itemsRevision.collectLatest { loadPendingIdeas() } }
    }

    /** Le bandeau est un confort : un échec garde le dernier compte sans déranger. */
    private suspend fun loadPendingIdeas() {
        runCatchingCancellable { items.pendingIdeas().size }
            .onSuccess { count -> local.update { it.copy(pendingIdeasCount = count) } }
    }

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

    fun requestLeave() {
        val group = uiState.value.group ?: return
        if (uiState.value.canLeave) local.update { it.copy(leaving = group) }
    }

    fun cancelLeave() = local.update { it.copy(leaving = null) }

    fun confirmLeave() {
        val group = local.value.leaving ?: return
        local.update { it.copy(leaving = null) }
        perform { session.leaveGroup(group) }
    }

    fun selectGroup(group: FamilyGroup) {
        if (group.id == uiState.value.group?.id) return
        perform { session.selectGroup(group) }
    }

    fun refresh() {
        local.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val failure = session.refreshGroupData().exceptionOrNull()
            loadPendingIdeas()
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
