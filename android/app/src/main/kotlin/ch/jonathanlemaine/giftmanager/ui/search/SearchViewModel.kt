package ch.jonathanlemaine.giftmanager.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.gifts.SearchFilters
import ch.jonathanlemaine.giftmanager.core.gifts.SearchGroup
import ch.jonathanlemaine.giftmanager.core.gifts.SearchList
import ch.jonathanlemaine.giftmanager.core.gifts.SearchLogic
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.currentEvent
import ch.jonathanlemaine.giftmanager.core.session.isParent
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/** Recherche dans les listes de la famille (iOS : `SearchView`, #11). */
data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    /** Résultats regroupés par enfant, dans l'ordre de la famille. */
    val groups: List<SearchGroup> = emptyList(),
    /** Pays de boutique proposés en filtre. */
    val countries: List<String> = emptyList(),
    /** Faux jusqu'à la fin du premier chargement (réussi ou non). */
    val hasLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: GiftError? = null,
) {
    val resultCount: Int get() = groups.sumOf { it.results.size }

    /** Aucun résultat une fois chargé : « Aucun résultat ». */
    val isEmpty: Boolean get() = hasLoaded && groups.isEmpty()

    val hasQuery: Boolean get() = filters.query.isNotBlank()
}

class SearchViewModel(
    private val session: AppSession,
    private val items: ItemRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    /** Ce que contient une liste après chargement ; l'enfant lui-même est relu dans la session (son nom suit). */
    private class Loaded(val items: List<WishItem>, val links: Map<UUID, List<ItemLink>>)

    private data class Local(
        val filters: SearchFilters = SearchFilters(),
        val loaded: Map<UUID, Loaded> = emptyMap(),
        val hasLoaded: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: GiftError? = null,
    )

    /** Ce qui déclenche un rechargement avec la révision des cadeaux : les listes visibles et l'événement courant. */
    private data class LoadKey(val childIds: List<UUID>, val eventId: UUID?)

    private val local = MutableStateFlow(Local())

    // Les résultats se dérivent des listes chargées et des filtres : taper ne fait aucune requête.
    val uiState: StateFlow<SearchUiState> = combine(local, session.state) { l, s -> build(l, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(local.value, session.state.value))

    private fun build(l: Local, s: SessionState): SearchUiState {
        // Dans l'ordre des enfants de la famille ; un enfant parti ou pas encore chargé est omis.
        val lists = s.children.mapNotNull { child ->
            l.loaded[child.id]?.let { SearchList(child, isParentList = s.isParent(child), items = it.items, links = it.links) }
        }
        return SearchUiState(
            filters = l.filters,
            groups = SearchLogic.groups(lists, l.filters, s.profile?.country),
            countries = SearchLogic.countries(lists),
            hasLoaded = l.hasLoaded,
            isRefreshing = l.isRefreshing,
            error = l.error,
        )
    }

    private fun key(s: SessionState) = LoadKey(s.children.map { it.id }, s.currentEvent(clock)?.id)

    init {
        // Au départ, à chaque changement de cadeaux, d'enfants ou d'événement courant ; collectLatest abandonne un
        // chargement devenu obsolète.
        viewModelScope.launch {
            combine(session.itemsRevision, session.state.map(::key).distinctUntilChanged()) { _, key -> key }
                .collectLatest { load(it) }
        }
    }

    /**
     * Charge, en parallèle, la liste de chaque enfant de la famille et les liens. Un enfant en échec n'empêche pas les
     * autres : ses cadeaux manquent, l'erreur est signalée, et il retrouve sa liste au chargement suivant.
     */
    private suspend fun load(key: LoadKey) {
        val outcomes = coroutineScope {
            key.childIds.map { childId ->
                async {
                    childId to runCatchingCancellable {
                        val fetched = items.childItems(childId, key.eventId)
                        Loaded(fetched, items.links(fetched.map { it.id }).groupBy { it.itemId })
                    }
                }
            }.map { it.await() }
        }
        val failure = outcomes.firstNotNullOfOrNull { it.second.exceptionOrNull() }
        val loaded = outcomes.mapNotNull { (id, result) -> result.getOrNull()?.let { id to it } }.toMap()
        local.update {
            // Un enfant en échec garde ce qu'on savait de lui plutôt que de disparaître des résultats.
            val kept = it.loaded.filterKeys { id -> id in key.childIds && id !in loaded }
            it.copy(loaded = kept + loaded, hasLoaded = true, isRefreshing = false, error = failure?.let(GiftError::from))
        }
    }

    // region Filtres

    fun onQueryChange(query: String) = local.update { it.copy(filters = it.filters.copy(query = query)) }

    fun toggleOnlyAvailable() = local.update { it.copy(filters = it.filters.copy(onlyAvailable = !it.filters.onlyAvailable)) }

    /** `null` : « Tous pays » ; choisir le pays déjà actif le retire. */
    fun selectCountry(country: String?) = local.update {
        it.copy(filters = it.filters.copy(country = country.takeIf { next -> next != it.filters.country }))
    }

    // endregion

    /** Tirer pour actualiser. */
    fun refresh() {
        local.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { load(key(session.state.value)) }
    }

    fun dismissError() = local.update { it.copy(error = null) }
}
