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
import ch.jonathanlemaine.giftmanager.ui.events.EventDetailActions
import ch.jonathanlemaine.giftmanager.ui.events.EventDetailScreen
import ch.jonathanlemaine.giftmanager.ui.events.EventDetailViewModel
import ch.jonathanlemaine.giftmanager.ui.events.EventEditorActions
import ch.jonathanlemaine.giftmanager.ui.events.EventEditorScreen
import ch.jonathanlemaine.giftmanager.ui.events.EventEditorViewModel
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.ui.gifts.AddGiftActions
import ch.jonathanlemaine.giftmanager.ui.gifts.AddGiftScreen
import ch.jonathanlemaine.giftmanager.ui.gifts.AddGiftViewModel
import ch.jonathanlemaine.giftmanager.ui.gifts.ChildGiftsActions
import ch.jonathanlemaine.giftmanager.ui.gifts.ChildGiftsScreen
import ch.jonathanlemaine.giftmanager.ui.gifts.ChildGiftsViewModel
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftDetailActions
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftDetailScreen
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftDetailViewModel
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
            onRefresh = viewModel::refresh,
        ),
    )
}

/** Liste de cadeaux d'un enfant ; se ferme si l'enfant disparaît de la famille. */
@Composable
fun ChildGiftsDestination(
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    onOpenItem: (UUID) -> Unit,
    /** Ouvre le formulaire d'ajout : enfant, type, événement affiché, cadeau déjà possédé. */
    onAdd: (childId: UUID, kind: WishKind, eventId: UUID?, owned: Boolean) -> Unit,
) {
    val viewModel: ChildGiftsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isClosed) { if (state.isClosed) onBack() }
    ChildGiftsScreen(
        state = state,
        actions = ChildGiftsActions(
            onBack = onBack,
            onSelectTab = viewModel::selectTab,
            onSelectEvent = viewModel::selectEvent,
            onOpenItem = { onOpenItem(it.id) },
            onAdd = {
                val mode = state.addMode
                val child = state.child
                if (mode != null && child != null) onAdd(child.id, mode.kind, state.eventId, mode.owned)
            },
            onDelete = viewModel::delete,
            onToggleOwned = viewModel::toggleOwned,
            onToggleFavorite = viewModel::toggleFavorite,
            onMove = viewModel::move,
            onRefresh = viewModel::refresh,
            onDismissError = viewModel::dismissError,
        ),
    )
}

/**
 * Fiche d'un cadeau, chargée par son seul identifiant ; se ferme si le cadeau n'existe plus ou vient d'être
 * supprimé. La participation à une cagnotte ([onJoinPot]) est branchée avec les cagnottes.
 */
@Composable
fun GiftDetailDestination(
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    /** Ouvre le formulaire de modification : enfant, type, événement, cadeau. */
    onEdit: (childId: UUID, kind: WishKind, eventId: UUID?, itemId: UUID) -> Unit,
    onJoinPot: (itemId: UUID) -> Unit = {},
) {
    val viewModel: GiftDetailViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isClosed) { if (state.isClosed) onBack() }
    // La célébration (« Réservé ! ») est consommée dès qu'elle est affichée.
    LaunchedEffect(state.celebration) { if (state.celebration != null) viewModel.consumeCelebration() }
    GiftDetailScreen(
        state = state,
        actions = GiftDetailActions(
            onBack = onBack,
            onEdit = { state.item?.let { onEdit(it.childId, it.kind, it.eventId, it.id) } },
            onReserve = viewModel::reserve,
            onCancelReservation = viewModel::cancelReservation,
            onSetPurchased = viewModel::setPurchased,
            onToggleOwned = viewModel::toggleOwned,
            onMarkReceived = viewModel::markReceived,
            onDelete = viewModel::delete,
            onJoinPot = { state.item?.let { onJoinPot(it.id) } },
            onLeavePot = viewModel::leavePot,
            onDismissError = viewModel::dismissError,
        ),
    )
}

/** Formulaire d'ajout ou de modification d'un cadeau ; se ferme une fois enregistré. */
@Composable
fun AddGiftDestination(viewModelFactory: ViewModelProvider.Factory, onClose: () -> Unit) {
    val viewModel: AddGiftViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    AddGiftScreen(
        state = state,
        actions = AddGiftActions(
            onClose = onClose,
            onUrlChange = viewModel::onUrlChange,
            onUrlSubmit = viewModel::onUrlSubmit,
            onTitleChange = viewModel::onTitleChange,
            onNotesChange = viewModel::onNotesChange,
            onPriceChange = viewModel::onPriceChange,
            onFavoriteChange = viewModel::onFavoriteChange,
            onSubmitToParentsChange = viewModel::onSubmitToParentsChange,
            onSave = viewModel::saveChecked,
            onConfirmDuplicate = viewModel::save,
            onDismissDuplicate = viewModel::dismissDuplicate,
            onDismissError = viewModel::dismissError,
        ),
    )
}
