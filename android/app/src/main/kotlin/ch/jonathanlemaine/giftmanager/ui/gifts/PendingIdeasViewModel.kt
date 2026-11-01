package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.PendingIdea
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.data.ItemNotifier
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class PendingIdeasUiState(
    val ideas: List<PendingIdea> = emptyList(),
    /** Faux jusqu'au premier chargement : l'écran affiche un indicateur plutôt que « Aucune proposition ». */
    val hasLoaded: Boolean = false,
    /** Idée dont le verdict est en cours d'envoi : ses boutons sont bloqués. */
    val workingId: UUID? = null,
    val error: GiftError? = null,
)

/**
 * Idées proposées pour mes enfants, en attente de mon verdict (#57, iOS : `PendingIdeasView`). Le parent accepte,
 * refuse ou signale que l'enfant l'a déjà, sans jamais apprendre qui offrira le cadeau.
 */
class PendingIdeasViewModel(
    private val session: AppSession,
    private val items: ItemRepository,
    private val notifier: ItemNotifier,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PendingIdeasUiState())
    val uiState: StateFlow<PendingIdeasUiState> = _uiState.asStateFlow()

    init {
        // Au départ, puis à chaque changement de cadeaux (le verdict d'une idée en est un).
        viewModelScope.launch { session.itemsRevision.collectLatest { load() } }
    }

    private suspend fun load() {
        runCatchingCancellable { items.pendingIdeas() }
            .onSuccess { list -> _uiState.update { it.copy(ideas = list, hasLoaded = true) } }
            .onFailure { failure -> _uiState.update { it.copy(hasLoaded = true, error = GiftError.from(failure)) } }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun accept(idea: PendingIdea) = decide(idea, IdeaReview.ACCEPTED, null)

    fun ownedAlready(idea: PendingIdea) = decide(idea, IdeaReview.OWNED_ALREADY, null)

    /** [note] : un mot facultatif pour l'auteur ; un texte blanc vaut « sans note ». */
    fun reject(idea: PendingIdea, note: String? = null) = decide(idea, IdeaReview.REJECTED, note)

    private fun decide(idea: PendingIdea, decision: IdeaReview, note: String?) {
        if (_uiState.value.workingId != null) return
        _uiState.update { it.copy(workingId = idea.id, error = null) }
        viewModelScope.launch {
            val failure = runCatchingCancellable {
                items.reviewIdea(idea.id, decision, note?.trim()?.takeIf { it.isNotEmpty() })
            }.exceptionOrNull()
            if (failure == null) {
                _uiState.update { state -> state.copy(ideas = state.ideas.filterNot { it.id == idea.id }, workingId = null) }
                session.itemsChanged()
                // #57 : l'auteur est informé du verdict, personne d'autre. Sans attente.
                notifier.notifyIdeaReview(idea.id, decision)
            } else {
                // L'idée reste affichée : rien n'a été décidé.
                _uiState.update { it.copy(workingId = null, error = GiftError.from(failure)) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
