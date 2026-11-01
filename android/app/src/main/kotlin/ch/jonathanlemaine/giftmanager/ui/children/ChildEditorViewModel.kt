package ch.jonathanlemaine.giftmanager.ui.children

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.format.AvatarPalette
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.data.ChildRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce que l'éditeur fait (iOS : `ChildEditorView.Mode`). */
sealed interface ChildEditorMode {
    data object Create : ChildEditorMode

    /** Liste d'un adulte du foyer (moi, mon conjoint) : même mode surprise qu'un enfant (#37). */
    data object CreateAdult : ChildEditorMode

    data class Edit(val childId: UUID) : ChildEditorMode
}

data class ChildEditorUiState(
    val isAdult: Boolean = false,
    val isEditing: Boolean = false,
    /** Prénom enregistré (titre de l'écran, libellé de suppression) ; `null` en création. */
    val savedFirstName: String? = null,
    val firstName: String = "",
    val hasBirthdate: Boolean = true,
    val birthdate: LocalDate = LocalDate.now(),
    /** Aujourd'hui : on ne naît pas dans le futur. */
    val maxBirthdate: LocalDate = LocalDate.now(),
    val emoji: String? = AvatarPalette.defaultEmoji,
    val colorName: String = AvatarPalette.defaultColor,
    val isSaving: Boolean = false,
    val confirmDelete: Boolean = false,
    val error: GiftError? = null,
    /** L'écran doit se fermer (enregistré, supprimé, ou enfant introuvable). */
    val finished: Boolean = false,
) {
    val canSave: Boolean get() = firstName.trim().isNotEmpty() && !isSaving

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}

/** Ajout, modification et suppression d'un enfant ou d'une liste d'adulte (iOS : `ChildEditorView`). */
class ChildEditorViewModel(
    private val mode: ChildEditorMode,
    private val session: AppSession,
    private val childRepo: ChildRepository,
    clock: Clock = Clock.systemDefaultZone(),
    private val newId: () -> UUID = UUID::randomUUID,
) : ViewModel() {
    private val existing: Child? = (mode as? ChildEditorMode.Edit)?.let { session.state.value.child(it.childId) }

    private val _uiState = MutableStateFlow(initialState(clock))
    val uiState: StateFlow<ChildEditorUiState> = _uiState.asStateFlow()

    private fun initialState(clock: Clock): ChildEditorUiState {
        val today = LocalDate.now(clock)
        existing?.let { child ->
            return ChildEditorUiState(
                isAdult = child.isAdult,
                isEditing = true,
                savedFirstName = child.firstName,
                firstName = child.firstName,
                hasBirthdate = child.birthdate != null,
                birthdate = child.birthdate?.date ?: today.minusYears(DEFAULT_CHILD_AGE),
                maxBirthdate = today,
                emoji = child.avatarEmoji,
                colorName = child.avatarColor ?: AvatarPalette.defaultColor,
            )
        }
        val adult = mode == ChildEditorMode.CreateAdult
        return ChildEditorUiState(
            isAdult = adult,
            // Liste d'adulte : la date de naissance est facultative, désactivée par défaut.
            hasBirthdate = !adult,
            birthdate = today.minusYears(if (adult) DEFAULT_ADULT_AGE else DEFAULT_CHILD_AGE),
            maxBirthdate = today,
            // Enfant introuvable (supprimé entre-temps par l'autre parent) : rien à éditer.
            finished = mode is ChildEditorMode.Edit,
        )
    }

    fun onFirstNameChange(name: String) = _uiState.update { it.copy(firstName = name.take(ChildEditorUiState.MAX_NAME_LENGTH)) }

    fun onHasBirthdateChange(value: Boolean) = _uiState.update { it.copy(hasBirthdate = value) }

    fun onBirthdateChange(date: LocalDate) = _uiState.update { it.copy(birthdate = minOf(date, it.maxBirthdate)) }

    fun onEmojiChange(emoji: String) = _uiState.update { it.copy(emoji = emoji) }

    fun onColorChange(name: String) = _uiState.update { it.copy(colorName = name) }

    fun requestDelete() = _uiState.update { if (existing != null) it.copy(confirmDelete = true) else it }

    fun cancelDelete() = _uiState.update { it.copy(confirmDelete = false) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val householdId = existing?.householdId ?: session.state.value.myHousehold?.id
        if (householdId == null) {
            _uiState.update { it.copy(error = GiftError.NoHousehold) }
            return
        }
        val child = Child(
            id = existing?.id ?: newId(),
            householdId = householdId,
            firstName = state.firstName.trim(),
            birthdate = if (state.hasBirthdate) DayDate(state.birthdate) else null,
            avatarEmoji = state.emoji,
            avatarColor = state.colorName,
            avatarUrl = existing?.avatarUrl,
            isAdult = state.isAdult,
        )
        perform {
            childRepo.saveChild(child)
            session.reloadGroup()
        }
    }

    fun delete() {
        val child = existing ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(confirmDelete = false) }
        perform {
            childRepo.deleteChild(child.id)
            session.reloadGroup()
            session.itemsChanged()
        }
    }

    /**
     * Le rechargement de la famille après l'écriture n'est pas bloquant : si lui seul échoue, l'écriture a
     * réussi et les données se rafraîchiront au prochain chargement.
     */
    private fun perform(action: suspend () -> Unit) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val failure = runCatchingCancellable { action() }.exceptionOrNull()
            _uiState.update {
                if (failure == null) it.copy(isSaving = false, finished = true) else it.copy(isSaving = false, error = GiftError.from(failure))
            }
        }
    }

    private companion object {
        const val DEFAULT_CHILD_AGE = 6L
        const val DEFAULT_ADULT_AGE = 35L
    }
}
