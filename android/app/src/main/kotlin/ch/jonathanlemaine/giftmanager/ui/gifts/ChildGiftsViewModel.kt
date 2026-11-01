package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.gifts.AddMode
import ch.jonathanlemaine.giftmanager.core.gifts.GiftActionReducer
import ch.jonathanlemaine.giftmanager.core.gifts.GiftCounts
import ch.jonathanlemaine.giftmanager.core.gifts.GiftListLogic
import ch.jonathanlemaine.giftmanager.core.gifts.GiftTab
import ch.jonathanlemaine.giftmanager.core.gifts.StoreCatalog
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.core.session.household
import ch.jonathanlemaine.giftmanager.core.session.isParent
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Liste de cadeaux d'un enfant (iOS : `ChildGiftsView` + `GiftListModel`). */
data class ChildGiftsUiState(
    /** `null` si l'enfant a disparu de la famille (supprimé par l'autre parent) : l'écran se ferme. */
    val child: Child? = null,
    val householdName: String? = null,
    /** Liste d'un parent (mode surprise) : pas d'onglet Idées, aucun statut de réservation. */
    val isParent: Boolean = true,
    /** Événement passé : aucune modification ni réservation. */
    val readOnly: Boolean = false,
    /** Événement filtré ; `null` = « Tous les cadeaux » (#61). */
    val eventId: UUID? = null,
    val selectableEvents: List<GiftEvent> = emptyList(),
    val items: List<WishItem> = emptyList(),
    val links: Map<UUID, List<ItemLink>> = emptyMap(),
    val preferredCountry: String? = null,
    val currentUserId: UUID? = null,
    val tab: GiftTab = GiftTab.WISHES,
    val isLoading: Boolean = true,
    val hasLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: GiftError? = null,
) {
    val isClosed: Boolean get() = child == null

    val tabs: List<GiftTab> get() = GiftListLogic.tabs(isParent)

    val counts: GiftCounts get() = GiftListLogic.counts(items)

    val visibleItems: List<WishItem> get() = GiftListLogic.items(items, tab)

    /** Titre de l'événement filtré, ou `null` pour « Tous les cadeaux ». */
    val selectedEvent: GiftEvent? get() = selectableEvents.firstOrNull { it.id == eventId }

    val addMode: AddMode? get() = GiftListLogic.addMode(isParent, tab, readOnly)

    val canReorder: Boolean get() = GiftListLogic.canReorder(isParent, tab, items, readOnly)

    /** Liens d'un cadeau, ceux du pays du lecteur d'abord ; le premier donne le prix de la carte. */
    fun linksFor(item: WishItem): List<ItemLink> = StoreCatalog.sorted(links[item.id].orEmpty(), preferredCountry)

    fun canEdit(item: WishItem): Boolean = GiftListLogic.canEdit(item, isParent, currentUserId, readOnly)

    fun canToggleOwned(item: WishItem): Boolean = !readOnly && GiftListLogic.canToggleOwned(item, isParent)

    fun canToggleFavorite(item: WishItem): Boolean = GiftListLogic.canToggleFavorite(item, isParent, readOnly)
}

class ChildGiftsViewModel(
    private val childId: UUID,
    initialEventId: UUID?,
    private val readOnly: Boolean,
    private val session: AppSession,
    private val items: ItemRepository,
) : ViewModel() {
    private data class Local(
        val eventId: UUID?,
        val items: List<WishItem> = emptyList(),
        val links: Map<UUID, List<ItemLink>> = emptyMap(),
        val tab: GiftTab = GiftTab.WISHES,
        val isLoading: Boolean = true,
        val hasLoaded: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: GiftError? = null,
    )

    private val local = MutableStateFlow(Local(eventId = initialEventId))

    val uiState: StateFlow<ChildGiftsUiState> = combine(local, session.state) { l, s -> build(l, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(local.value, session.state.value))

    init {
        // Au départ, à chaque changement de cadeaux (ajout, réservation, suppression…) et à chaque changement de
        // filtre ; collectLatest abandonne un chargement devenu obsolète.
        viewModelScope.launch {
            combine(session.itemsRevision, local.map { it.eventId }.distinctUntilChanged()) { _, event -> event }
                .collectLatest { load(it) }
        }
    }

    private fun build(l: Local, s: SessionState): ChildGiftsUiState {
        val child = s.child(childId)
        val isParent = GiftListLogic.isParentList(child?.let { s.isParent(it) }, l.items)
        val tabs = GiftListLogic.tabs(isParent)
        return ChildGiftsUiState(
            child = child,
            householdName = child?.let { s.household(of = it)?.name },
            isParent = isParent,
            readOnly = readOnly,
            eventId = l.eventId,
            selectableEvents = GiftListLogic.selectableEvents(s.events, childId),
            items = l.items,
            links = l.links,
            preferredCountry = s.profile?.country,
            currentUserId = s.userId,
            // L'onglet Idées n'existe pas côté parents : on retombe sur la liste.
            tab = l.tab.takeIf { it in tabs } ?: GiftTab.WISHES,
            isLoading = l.isLoading,
            hasLoaded = l.hasLoaded,
            isRefreshing = l.isRefreshing,
            error = l.error,
        )
    }

    private suspend fun load(eventId: UUID?) {
        val result = runCatchingCancellable {
            val fetched = items.childItems(childId, eventId)
            fetched to items.links(fetched.map { it.id }).groupBy { it.itemId }
        }
        result.onSuccess { (fetched, links) ->
            local.update { it.copy(items = fetched, links = links, isLoading = false, hasLoaded = true, isRefreshing = false) }
        }.onFailure { failure ->
            local.update { it.copy(isLoading = false, isRefreshing = false, error = GiftError.from(failure)) }
        }
    }

    fun selectTab(tab: GiftTab) = local.update { it.copy(tab = tab) }

    /** Change l'événement affiché (`null` : tous les cadeaux) ; le chargement suit. */
    fun selectEvent(eventId: UUID?) = local.update {
        if (it.eventId == eventId) it else it.copy(eventId = eventId, hasLoaded = false)
    }

    /** Tirer pour actualiser. */
    fun refresh() {
        local.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { load(local.value.eventId) }
    }

    // region Actions

    fun delete(item: WishItem) {
        if (!uiState.value.canEdit(item)) return
        viewModelScope.launch {
            runCatchingCancellable { items.deleteItem(item.id) }
                .onSuccess {
                    local.update { it.copy(items = it.items.filter { other -> other.id != item.id }) }
                    session.itemsChanged()
                }
                .onFailure { failure -> local.update { it.copy(error = GiftError.from(failure)) } }
        }
    }

    /** « Il l'a déjà » / « Remettre dans la liste » : appliqué d'emblée, annulé si le serveur refuse. */
    fun toggleOwned(item: WishItem) {
        if (!uiState.value.canToggleOwned(item)) return
        val next = GiftActionReducer.ownedToggled(item)
        optimistic(item, next) { items.setOwned(item.id, next.owned) }
    }

    /** « Très envie » : appliqué d'emblée, annulé si le serveur refuse. */
    fun toggleFavorite(item: WishItem) {
        if (!uiState.value.canToggleFavorite(item)) return
        val next = GiftActionReducer.favoriteToggled(item)
        optimistic(item, next) { items.setPriority(item.id, next.isFavorite) }
    }

    private fun optimistic(previous: WishItem, next: WishItem, call: suspend () -> Unit) {
        replace(next)
        viewModelScope.launch {
            runCatchingCancellable { call() }
                .onSuccess { session.itemsChanged() }
                .onFailure { failure ->
                    replace(previous)
                    local.update { it.copy(error = GiftError.from(failure)) }
                }
        }
    }

    private fun replace(item: WishItem) = local.update { state ->
        state.copy(items = state.items.map { if (it.id == item.id) item else it })
    }

    /** Déplace un souhait (index dans l'onglet Liste) et enregistre l'ordre ; annulé si le serveur refuse. */
    fun move(from: Int, to: Int) {
        val state = uiState.value
        if (!state.canReorder || from == to) return
        val moved = GiftListLogic.move(state.items, from, to) ?: return
        val previous = local.value.items
        val positions = GiftListLogic.positions(previous)
        local.update { it.copy(items = moved.items) }
        viewModelScope.launch {
            runCatchingCancellable { items.reorder(moved.orderedIds, positions) }
                .onFailure { failure ->
                    local.update { it.copy(items = previous, error = GiftError.from(failure)) }
                }
        }
    }

    // endregion

    fun dismissError() = local.update { it.copy(error = null) }
}
