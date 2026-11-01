package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.model.ThanksNote
import ch.jonathanlemaine.giftmanager.data.ThanksRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Boîte des remerciements reçus (iOS : `ThanksInboxView`). */
data class ThanksInboxUiState(
    val notes: List<ThanksNote> = emptyList(),
    /** Faux jusqu'à la fin du premier chargement (réussi ou non). */
    val hasLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    /** Remerciements dont le choix « Me faire connaître » est en cours d'enregistrement. */
    val pending: Set<UUID> = emptySet(),
    val error: GiftError? = null,
) {
    val isEmpty: Boolean get() = hasLoaded && notes.isEmpty()
}

class ThanksInboxViewModel(private val thanks: ThanksRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ThanksInboxUiState())
    val uiState: StateFlow<ThanksInboxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    /** Tirer pour actualiser. */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        runCatchingCancellable { thanks.myThanks() }
            .onSuccess { notes -> _uiState.update { it.copy(notes = notes, hasLoaded = true, isRefreshing = false) } }
            .onFailure { failure -> _uiState.update { it.copy(hasLoaded = true, isRefreshing = false, error = GiftError.from(failure)) } }
    }

    /**
     * « Me faire connaître » : bascule d'emblée sur tous les remerciements du même cadeau (le choix vaut pour le
     * cadeau), puis l'enregistre. Un second appui est ignoré tant que le premier n'a pas abouti ; un échec remet
     * l'ancien choix et remonte l'erreur.
     */
    fun setRevealed(note: ThanksNote, value: Boolean) {
        val state = _uiState.value
        if (state.notes.any { it.id in state.pending && it.itemId == note.itemId }) return
        val ids = state.notes.filter { it.itemId == note.itemId }.map { it.id }.toSet()
        _uiState.update { it.copy(pending = it.pending + ids) }
        markRevealed(note.itemId, value)
        viewModelScope.launch {
            runCatchingCancellable { thanks.revealMyself(note.itemId, value) }
                .onFailure { failure ->
                    markRevealed(note.itemId, !value)
                    _uiState.update { it.copy(error = GiftError.from(failure)) }
                }
            _uiState.update { it.copy(pending = it.pending - ids) }
        }
    }

    private fun markRevealed(itemId: UUID, value: Boolean) = _uiState.update { state ->
        state.copy(notes = state.notes.map { if (it.itemId == itemId) it.copy(revealed = value) else it })
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
