package ch.jonathanlemaine.giftmanager.ui.childmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeItem
import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLock
import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLogic
import ch.jonathanlemaine.giftmanager.core.childmode.ParentAuthenticator
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Mode enfant (iOS : `ChildModeView` + `ChildModeModel`) : l'enfant parcourt sa liste et pose des cœurs « très envie ».
 * L'état ne porte que des [ChildModeItem] : ni prix, ni statut, ni réservation, ni notes.
 */
data class ChildModeUiState(
    val isLoading: Boolean = true,
    /** Prénom, avatar : l'en-tête « Les envies de Léa ». `null` si l'enfant a quitté la famille. */
    val child: Child? = null,
    val items: List<ChildModeItem> = emptyList(),
    /** Cœurs en cours d'enregistrement : un second appui est ignoré jusqu'à la réponse. */
    val saving: Set<UUID> = emptySet(),
    /** Au moins un cœur a été enregistré : la liste du parent se recharge à la sortie. */
    val didChange: Boolean = false,
    /** Un cœur n'a pas pu être enregistré (« Oups, réessaie ! ») ; effacé par [ChildModeViewModel.dismissSaveFailed]. */
    val saveFailed: Boolean = false,
    /** La liste n'a pas pu être chargée. */
    val error: GiftError? = null,
    /** La sortie a été validée : l'écran se ferme. */
    val exited: Boolean = false,
    /** Une vérification de l'adulte est en cours (l'invite biométrique est affichée). */
    val isAuthenticating: Boolean = false,
) {
    val favoriteCount: Int get() = ChildModeLogic.favoriteCount(items)
}

class ChildModeViewModel(
    private val childId: UUID,
    private val eventId: UUID?,
    private val session: AppSession,
    private val itemRepository: ItemRepository,
    private val lock: ChildModeLock,
    private val authenticator: ParentAuthenticator,
) : ViewModel() {
    private val _local = MutableStateFlow(ChildModeUiState())

    val uiState: StateFlow<ChildModeUiState> = combine(_local, session.state) { local, s -> derive(local, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, derive(_local.value, session.state.value))

    private fun derive(local: ChildModeUiState, s: SessionState) = local.copy(child = s.child(childId))

    init {
        // Verrouillé dès l'ouverture : aucun dialogue de l'app ne doit apparaître devant l'enfant.
        lock.activate()
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        runCatchingCancellable { itemRepository.childItems(childId, eventId, WishKind.WISH) }
            // Projeté tout de suite : les `WishItem` (statut, notes, réservation…) ne sont pas conservés.
            .onSuccess { fetched -> _local.update { it.copy(isLoading = false, items = ChildModeLogic.visibleItems(fetched)) } }
            .onFailure { failure -> _local.update { it.copy(isLoading = false, error = GiftError.from(failure)) } }
    }

    /**
     * Bascule le cœur tout de suite, puis l'enregistre sur le souhait. En cas d'échec, le cœur revient à son état
     * précédent et [ChildModeUiState.saveFailed] s'allume. Ignoré si ce cœur s'enregistre déjà ou si le souhait est inconnu.
     */
    fun toggleFavorite(id: UUID) {
        val current = _local.value
        val item = current.items.firstOrNull { it.id == id } ?: return
        if (id in current.saving) return
        val next = !item.isFavorite
        _local.update { it.copy(items = it.items.withFavorite(id, next), saving = it.saving + id, saveFailed = false) }
        viewModelScope.launch {
            runCatchingCancellable { itemRepository.setPriority(id, next) }
                .onSuccess { _local.update { it.copy(saving = it.saving - id, didChange = true) } }
                .onFailure {
                    _local.update { it.copy(items = it.items.withFavorite(id, !next), saving = it.saving - id, saveFailed = true) }
                }
        }
    }

    private fun List<ChildModeItem>.withFavorite(id: UUID, favorite: Boolean) =
        map { if (it.id == id) it.copy(isFavorite = favorite) else it }

    fun dismissSaveFailed() = _local.update { it.copy(saveFailed = false) }

    /**
     * Appelée quand l'anneau « Quitter » est plein : l'adulte doit se vérifier (empreinte, visage ou code). Refus ou
     * annulation : on reste en mode enfant.
     */
    fun requestExit() {
        if (_local.value.isAuthenticating || _local.value.exited) return
        _local.update { it.copy(isAuthenticating = true) }
        viewModelScope.launch {
            val allowed = runCatchingCancellable { authenticator.authenticate() }.getOrDefault(false)
            _local.update { it.copy(isAuthenticating = false) }
            if (allowed) exit()
        }
    }

    /** Sort du mode enfant : relâche le verrou et, si un cœur a changé, recharge les listes. */
    fun exit() {
        if (_local.value.exited) return
        lock.release()
        if (_local.value.didChange) session.itemsChanged()
        _local.update { it.copy(exited = true) }
    }

    /** Vérification possible : l'accessibilité propose alors l'activation directe au lieu de l'appui long. */
    val isAuthenticationAvailable: Boolean get() = authenticator.isAvailable()

    override fun onCleared() {
        // Écran détruit sans passer par la sortie (retour système, processus) : le verrou ne doit pas rester.
        lock.release()
        if (!_local.value.exited && _local.value.didChange) session.itemsChanged()
    }
}
