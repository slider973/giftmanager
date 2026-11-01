package ch.jonathanlemaine.giftmanager.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.canDelete
import ch.jonathanlemaine.giftmanager.core.session.event
import ch.jonathanlemaine.giftmanager.data.EventRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce que l'éditeur fait (iOS : `EventEditorView.Mode`). */
sealed interface EventEditorMode {
    data object Create : EventEditorMode

    data class Edit(val eventId: UUID) : EventEditorMode
}

data class EventEditorUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val date: LocalDate = LocalDate.now(),
    /** Seul l'auteur (ou un parent, pour un anniversaire) peut supprimer : même règle que la RLS. */
    val canDelete: Boolean = false,
    val isSaving: Boolean = false,
    val confirmDelete: Boolean = false,
    val error: GiftError? = null,
    /** L'écran doit se fermer (enregistré, supprimé, ou événement introuvable). */
    val finished: Boolean = false,
) {
    val canSave: Boolean get() = title.trim().isNotEmpty() && !isSaving

    companion object {
        const val MAX_TITLE_LENGTH = 80
    }
}

/**
 * Création et modification d'un événement (iOS : `EventEditorView`). Android ne propose que le type « autre » :
 * Noël est global et les anniversaires sont créés par le serveur (la RLS refuse le reste).
 */
class EventEditorViewModel(
    mode: EventEditorMode,
    private val session: AppSession,
    private val eventRepo: EventRepository,
    clock: Clock = Clock.systemDefaultZone(),
    private val newId: () -> UUID = UUID::randomUUID,
) : ViewModel() {
    private val existing: GiftEvent? = (mode as? EventEditorMode.Edit)?.let { session.state.value.event(it.eventId) }

    private val _uiState = MutableStateFlow(
        existing?.let {
            EventEditorUiState(
                isEditing = true,
                title = it.title,
                date = it.eventDate.date,
                canDelete = session.state.value.canDelete(it),
            )
        } ?: EventEditorUiState(
            date = LocalDate.now(clock),
            // Événement introuvable (supprimé entre-temps) : rien à éditer.
            finished = mode is EventEditorMode.Edit,
        ),
    )
    val uiState: StateFlow<EventEditorUiState> = _uiState.asStateFlow()

    fun onTitleChange(title: String) = _uiState.update { it.copy(title = title.take(EventEditorUiState.MAX_TITLE_LENGTH)) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }

    fun requestDelete() = _uiState.update { if (it.canDelete) it.copy(confirmDelete = true) else it }

    fun cancelDelete() = _uiState.update { it.copy(confirmDelete = false) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val group = session.state.value.currentGroup
        if (group == null) {
            _uiState.update { it.copy(error = GiftError.NotAMember) }
            return
        }
        val event = GiftEvent(
            id = existing?.id ?: newId(),
            groupId = group.id,
            kind = GiftEventKind.OTHER,
            title = state.title.trim(),
            eventDate = DayDate(state.date),
            childId = null,
            createdBy = existing?.createdBy,
        )
        perform { eventRepo.saveEvent(event) }
    }

    fun delete() {
        val event = existing ?: return
        if (!_uiState.value.canDelete || _uiState.value.isSaving) return
        _uiState.update { it.copy(confirmDelete = false) }
        perform { eventRepo.deleteEvent(event.id) }
    }

    /** Écrit, puis recharge la famille ; un échec du seul rechargement n'annule pas l'écriture. */
    private fun perform(write: suspend () -> Unit) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val failure = runCatchingCancellable { write() }.exceptionOrNull()
            if (failure == null) session.reloadGroup()
            _uiState.update {
                if (failure == null) it.copy(isSaving = false, finished = true) else it.copy(isSaving = false, error = GiftError.from(failure))
            }
        }
    }
}
