package ch.jonathanlemaine.giftmanager.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.jonathanlemaine.giftmanager.GiftManagerApp
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
import ch.jonathanlemaine.giftmanager.ui.gifts.JoinPotActions
import ch.jonathanlemaine.giftmanager.ui.gifts.JoinPotSheet
import ch.jonathanlemaine.giftmanager.ui.gifts.JoinPotViewModel
import ch.jonathanlemaine.giftmanager.ui.gifts.PendingIdeasActions
import ch.jonathanlemaine.giftmanager.ui.gifts.PendingIdeasScreen
import ch.jonathanlemaine.giftmanager.ui.gifts.PendingIdeasViewModel
import java.util.UUID
import kotlinx.coroutines.launch

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
 * supprimé. « Participer à une cagnotte » ouvre [JoinPotSheet] ; une fois la part enregistrée, la fiche se recharge
 * et célèbre ([GiftDetailViewModel.onPotJoined]).
 */
@Composable
fun GiftDetailDestination(
    viewModelFactory: ViewModelProvider.Factory,
    onBack: () -> Unit,
    /** Ouvre le formulaire de modification : enfant, type, événement, cadeau. */
    onEdit: (childId: UUID, kind: WishKind, eventId: UUID?, itemId: UUID) -> Unit,
) {
    val viewModel: GiftDetailViewModel = viewModel(factory = viewModelFactory)
    // Chaque ouverture de la feuille a son ViewModel (clé distincte) : une feuille fermée ne revient pas « terminée ».
    var joinPotSession by rememberSaveable { mutableIntStateOf(0) }
    var joinPotOpen by rememberSaveable { mutableStateOf(false) }
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
            onJoinPot = {
                joinPotSession++
                joinPotOpen = true
            },
            onLeavePot = viewModel::leavePot,
            onDismissError = viewModel::dismissError,
            onToggleFavorite = viewModel::toggleFavorite,
        ),
    )
    val itemId = state.item?.id
    if (joinPotOpen && itemId != null) {
        JoinPotDestination(
            viewModelFactory = viewModelFactory,
            itemId = itemId,
            session = joinPotSession,
            onDismiss = { joinPotOpen = false },
            onJoined = {
                joinPotOpen = false
                viewModel.onPotJoined()
            },
        )
    }
}

/** Feuille de participation à une cagnotte : se ferme (et appelle [onJoined]) une fois la part enregistrée. */
@Composable
private fun JoinPotDestination(
    viewModelFactory: ViewModelProvider.Factory,
    itemId: UUID,
    session: Int,
    onDismiss: () -> Unit,
    onJoined: () -> Unit,
) {
    val owner = checkNotNull(LocalViewModelStoreOwner.current)
    val extras = MutableCreationExtras((owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras ?: CreationExtras.Empty)
        .apply { set(JoinPotViewModel.ITEM_ID_KEY, itemId) }
    val viewModel: JoinPotViewModel = viewModel(key = "join-pot-$itemId-$session", factory = viewModelFactory, extras = extras)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onJoined() }
    JoinPotSheet(
        state = state,
        actions = JoinPotActions(
            onDismiss = onDismiss,
            onAmountChange = viewModel::onAmountChange,
            onCurrencyChange = viewModel::onCurrencyChange,
            onSuggestion = viewModel::onSuggestion,
            onSave = viewModel::save,
            onDismissError = viewModel::dismissError,
        ),
    )
}

/** Idées en attente de mon verdict (#57). */
@Composable
fun PendingIdeasDestination(viewModelFactory: ViewModelProvider.Factory, onBack: () -> Unit) {
    val viewModel: PendingIdeasViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PendingIdeasScreen(
        state = state,
        actions = PendingIdeasActions(
            onBack = onBack,
            onAccept = viewModel::accept,
            onOwnedAlready = viewModel::ownedAlready,
            onReject = viewModel::reject,
            onRefresh = viewModel::refresh,
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
    // Photo choisie : redimensionnée en JPEG hors du fil principal (AndroidImageResizer), puis confiée au ViewModel.
    val resizer = (LocalContext.current.applicationContext as GiftManagerApp).container.imageResizer
    val scope = rememberCoroutineScope()
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
            onCurrencyChange = viewModel::onCurrencyChange,
            onEventChange = viewModel::onEventChange,
            onPhotoPicked = { uri -> scope.launch { resizer.resizedJpeg(uri)?.let(viewModel::onImagePicked) } },
            onAddLink = viewModel::addLink,
            onRemoveLink = viewModel::removeLink,
        ),
    )
}
