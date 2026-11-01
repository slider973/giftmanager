package ch.jonathanlemaine.giftmanager.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.jonathanlemaine.giftmanager.ui.children.ChildEditorActions
import ch.jonathanlemaine.giftmanager.ui.children.ChildEditorScreen
import ch.jonathanlemaine.giftmanager.ui.children.ChildEditorViewModel
import ch.jonathanlemaine.giftmanager.ui.children.ChildGiftsPlaceholder
import ch.jonathanlemaine.giftmanager.ui.children.ChildGiftsViewModel
import ch.jonathanlemaine.giftmanager.ui.events.EventDetailActions
import ch.jonathanlemaine.giftmanager.ui.events.EventDetailScreen
import ch.jonathanlemaine.giftmanager.ui.events.EventDetailViewModel
import ch.jonathanlemaine.giftmanager.ui.events.EventEditorActions
import ch.jonathanlemaine.giftmanager.ui.events.EventEditorScreen
import ch.jonathanlemaine.giftmanager.ui.events.EventEditorViewModel
import java.util.UUID

// Raccord entre les routes plein écran du graphe Accueil (#72) et leurs ViewModel. Les ViewModel lisent leur
// argument dans la route (voir `AppContainer.viewModelFactory`) ; ces composables n'ont donc que des rappels.

@Composable
fun ChildEditorDestination(viewModelFactory: ViewModelProvider.Factory, onClose: () -> Unit) {
    val viewModel: ChildEditorViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    ChildEditorScreen(
        state = state,
        actions = ChildEditorActions(
            onFirstNameChange = viewModel::onFirstNameChange,
            onHasBirthdateChange = viewModel::onHasBirthdateChange,
            onBirthdateChange = viewModel::onBirthdateChange,
            onEmojiChange = viewModel::onEmojiChange,
            onColorChange = viewModel::onColorChange,
            onSave = viewModel::save,
            onRequestDelete = viewModel::requestDelete,
            onCancelDelete = viewModel::cancelDelete,
            onDelete = viewModel::delete,
            onDismissError = viewModel::dismissError,
            onClose = onClose,
        ),
    )
}

@Composable
fun EventEditorDestination(viewModelFactory: ViewModelProvider.Factory, onClose: () -> Unit) {
    val viewModel: EventEditorViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    EventEditorScreen(
        state = state,
        actions = EventEditorActions(
            onTitleChange = viewModel::onTitleChange,
            onDateChange = viewModel::onDateChange,
            onSave = viewModel::save,
            onRequestDelete = viewModel::requestDelete,
            onCancelDelete = viewModel::cancelDelete,
            onDelete = viewModel::delete,
            onDismissError = viewModel::dismissError,
            onClose = onClose,
        ),
    )
}

/** Fiche d'un événement ; se ferme si l'événement disparaît (supprimé depuis l'éditeur ou par un autre membre). */
@Composable
fun EventDetailDestination(
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    onEdit: (UUID) -> Unit,
    /** Ouvre une liste : identifiant de l'enfant, identifiant de l'événement, lecture seule (événement passé). */
    onOpenChild: (childId: UUID, eventId: UUID, readOnly: Boolean) -> Unit,
) {
    val viewModel: EventDetailViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isClosed) { if (state.isClosed) onBack() }
    EventDetailScreen(
        state = state,
        actions = EventDetailActions(
            onBack = onBack,
            onEdit = onEdit,
            onOpenChild = { childId -> state.event?.let { onOpenChild(childId, it.id, state.readOnly) } },
            onDismissError = viewModel::dismissError,
        ),
    )
}

/** Remplacé par la vraie liste de cadeaux avec #73 (même route, mêmes arguments). */
@Composable
fun ChildGiftsDestination(viewModelFactory: ViewModelProvider.Factory, onBack: () -> Unit) {
    val viewModel: ChildGiftsViewModel = viewModel(factory = viewModelFactory)
    val name by viewModel.childName.collectAsStateWithLifecycle()
    ChildGiftsPlaceholder(childName = name, onBack = onBack)
}
