package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.gifts.AddGiftLogic
import ch.jonathanlemaine.giftmanager.core.gifts.GiftDraft
import ch.jonathanlemaine.giftmanager.core.gifts.GiftListLogic
import ch.jonathanlemaine.giftmanager.core.gifts.LinkPreviewParser
import ch.jonathanlemaine.giftmanager.core.gifts.LinkPreviewService
import ch.jonathanlemaine.giftmanager.core.gifts.StoreCatalog
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.DraftLink
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.core.session.household
import ch.jonathanlemaine.giftmanager.core.session.upcomingEvents
import ch.jonathanlemaine.giftmanager.data.ImageUploader
import ch.jonathanlemaine.giftmanager.data.ItemNotifier
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formulaire d'ajout ou de modification d'un cadeau (iOS : `AddGiftView` + `AddGiftModel`). */
data class AddGiftUiState(
    val kind: WishKind,
    val owned: Boolean,
    val isEditing: Boolean,
    /** Modification : le cadeau existant est en cours de chargement. */
    val isLoadingExisting: Boolean = false,
    val child: Child? = null,
    val parentNames: List<String> = emptyList(),
    val selectableEvents: List<GiftEvent> = emptyList(),
    val urlText: String = "",
    val title: String = "",
    val notes: String = "",
    val priceText: String = "",
    val currency: String = "CHF",
    val store: StoreCatalog.Store? = null,
    val imageUrl: String? = null,
    /** Photo choisie (JPEG redimensionné), envoyée à l'enregistrement. */
    val imageBytes: ByteArray? = null,
    val isFavorite: Boolean = false,
    val eventId: UUID? = null,
    val otherLinks: List<DraftLink> = emptyList(),
    val isFetching: Boolean = false,
    val previewFailed: Boolean = false,
    /** #57 : idée soumise à la validation des parents — activé par défaut. */
    val submitToParents: Boolean = true,
    /** Nombre de personnes qui verraient l'idée si elle n'est pas soumise ; `null` tant que non chargé. */
    val audience: Int? = null,
    /** Doublon trouvé avant enregistrement : l'écran demande confirmation (#61). */
    val duplicate: WishItem? = null,
    val isSaving: Boolean = false,
    val error: GiftError? = null,
    /** Enregistré (ou cadeau introuvable en modification) : l'écran se ferme. */
    val finished: Boolean = false,
) {
    val canSave: Boolean get() = title.trim().isNotEmpty() && !isSaving && !isLoadingExisting

    /** Une idée non soumise n'atteindrait personne. */
    val audienceIsEmpty: Boolean get() = kind == WishKind.IDEA && !submitToParents && audience == 0

    /** Options propres au souhait (« Très envie ») : jamais pour une idée ni un cadeau déjà possédé. */
    val showsFavorite: Boolean get() = kind == WishKind.WISH && !owned

    /** « Soumettre aux parents » : seulement à la création d'une idée. */
    val showsSubmitToParents: Boolean get() = kind == WishKind.IDEA && !isEditing

    /** « Pour » (événement) : un cadeau déjà possédé n'appartient à aucun événement. */
    val showsEventPicker: Boolean get() = !owned
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AddGiftViewModel(
    private val childId: UUID,
    private val kind: WishKind,
    private val owned: Boolean,
    eventId: UUID?,
    private val itemId: UUID?,
    private val session: AppSession,
    private val items: ItemRepository,
    private val images: ImageUploader,
    private val notifier: ItemNotifier,
    private val previews: LinkPreviewService,
    previewDebounceMs: Long = PREVIEW_DEBOUNCE_MS,
    /** Lien partagé depuis une autre app (#79) : pré-remplit la création et lance l'aperçu. */
    initialUrl: String? = null,
) : ViewModel() {
    private val _local = MutableStateFlow(
        AddGiftUiState(
            kind = kind,
            owned = owned,
            isEditing = itemId != null,
            isLoadingExisting = itemId != null,
            currency = session.state.value.profile?.currency ?: "CHF",
            eventId = eventId,
        ),
    )

    val uiState: StateFlow<AddGiftUiState> = combine(_local, session.state) { local, s -> derive(local, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, derive(_local.value, session.state.value))

    private fun derive(local: AddGiftUiState, s: SessionState): AddGiftUiState {
        val child = s.child(childId)
        return local.copy(
            child = child,
            parentNames = child?.let { c -> s.household(of = c)?.let(s::parents) }.orEmpty().map { it.displayName }.filter { it.isNotEmpty() },
            selectableEvents = GiftListLogic.selectableEvents(s.upcomingEvents(), childId),
        )
    }

    private val typedUrls = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val submittedUrls = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Lien d'origine en modification : pas d'aperçu pour lui (titre et photo conservés). */
    private var originalUrl: String? = null
    private var existing: WishItem? = null

    init {
        // Aperçu : 700 ms après la dernière frappe, ou tout de suite à la validation ; un nouveau lien abandonne
        // l'aperçu en cours (mapLatest).
        viewModelScope.launch {
            merge(typedUrls.debounce(previewDebounceMs), submittedUrls).mapLatest { fetchPreview(it) }.collect {}
        }
        if (itemId != null) {
            viewModelScope.launch { loadExisting(itemId) }
        } else {
            viewModelScope.launch { loadAudience() }
            // Après la mise en place des flux : l'aperçu part avec le délai habituel.
            initialUrl?.let(::onUrlChange)
        }
    }

    // region Chargements

    private suspend fun loadExisting(id: UUID) {
        val loader = ItemLoader(items)
        runCatchingCancellable { loader.load(id, session.state.value.events) }
            .onSuccess { loaded ->
                existing = loaded.item
                val main = loaded.links.firstOrNull()
                originalUrl = main?.url
                _local.update {
                    it.copy(
                        isLoadingExisting = false,
                        title = loaded.item.title,
                        notes = loaded.item.notes.orEmpty(),
                        imageUrl = loaded.item.imageUrl,
                        isFavorite = loaded.item.isFavorite,
                        eventId = loaded.item.eventId,
                        urlText = main?.url.orEmpty(),
                        store = main?.let { m -> StoreCatalog.Store(m.store.orEmpty(), m.country, m.currency) },
                        priceText = main?.price?.let(AddGiftLogic::priceInputText).orEmpty(),
                        currency = main?.currency ?: it.currency,
                        otherLinks = loaded.links.drop(1).map { l ->
                            DraftLink(url = l.url, store = l.store, country = l.country, price = l.price, currency = l.currency)
                        },
                    )
                }
            }
            .onFailure { failure -> _local.update { it.copy(isLoadingExisting = false, error = GiftError.from(failure), finished = true) } }
    }

    /** Combien de personnes verront cette idée (#57) ; ignoré pour un souhait. */
    private suspend fun loadAudience() {
        if (kind != WishKind.IDEA) return
        runCatchingCancellable { items.ideaAudience(childId) }.onSuccess { count -> _local.update { it.copy(audience = count) } }
    }

    // endregion

    // region Champs

    fun onUrlChange(text: String) {
        _local.update { it.copy(urlText = text) }
        // En modification, ne pas relancer l'aperçu pour le lien d'origine.
        if (itemId != null && text == originalUrl) return
        typedUrls.tryEmit(text)
    }

    /** Validation du champ de lien : aperçu immédiat. */
    fun onUrlSubmit() {
        submittedUrls.tryEmit(_local.value.urlText)
    }

    fun onTitleChange(text: String) = _local.update { it.copy(title = text) }

    fun onNotesChange(text: String) = _local.update { it.copy(notes = text) }

    fun onPriceChange(text: String) = _local.update { it.copy(priceText = text) }

    fun onCurrencyChange(code: String) = _local.update { it.copy(currency = code) }

    fun onFavoriteChange(value: Boolean) = _local.update { it.copy(isFavorite = value) }

    fun onEventChange(eventId: UUID?) = _local.update { it.copy(eventId = eventId) }

    fun onSubmitToParentsChange(value: Boolean) = _local.update { it.copy(submitToParents = value) }

    /** Photo choisie par l'utilisateur, déjà redimensionnée en JPEG : elle remplace l'image de l'aperçu. */
    fun onImagePicked(jpeg: ByteArray) = _local.update { it.copy(imageBytes = jpeg, imageUrl = null) }

    fun onImageRemoved() = _local.update { it.copy(imageBytes = null, imageUrl = null) }

    fun addLink(link: DraftLink) = _local.update { it.copy(otherLinks = it.otherLinks + link) }

    fun removeLink(id: UUID) = _local.update { it.copy(otherLinks = it.otherLinks.filter { link -> link.id != id }) }

    fun dismissError() = _local.update { it.copy(error = null) }

    fun dismissDuplicate() = _local.update { it.copy(duplicate = null) }

    // endregion

    // region Aperçu

    private suspend fun fetchPreview(text: String) {
        val url = LinkPreviewParser.normalizedUrl(text) ?: return
        val store = StoreCatalog.store(url)
        val editing = itemId != null
        _local.update {
            it.copy(
                store = store,
                currency = store?.currency ?: it.currency,
                // Nouveau lien : on efface le prix précédent pour ne jamais l'afficher avec une autre devise.
                priceText = if (editing) it.priceText else "",
                isFetching = true,
                previewFailed = false,
            )
        }
        val preview = runCatchingCancellable { previews.preview(url) }.getOrNull()
        // Un autre lien a été collé entre-temps : ce résultat est ignoré.
        if (_local.value.urlText != text) return
        _local.update { state ->
            if (preview == null) return@update state.copy(isFetching = false, previewFailed = true)
            state.copy(
                isFetching = false,
                title = if (state.title.isBlank() || !editing) preview.title ?: state.title else state.title,
                imageUrl = preview.imageUrl ?: state.imageUrl,
                imageBytes = if (preview.imageUrl != null) null else state.imageBytes,
                priceText = preview.price?.let(AddGiftLogic::priceInputText) ?: state.priceText,
                currency = preview.currency ?: state.currency,
            )
        }
    }

    // endregion

    // region Enregistrement

    /** Enregistre, après recherche de doublon pour une création (#61). Un doublon demande confirmation. */
    fun saveChecked() {
        val state = _local.value
        if (!state.canSave) return
        if (itemId != null) {
            save()
            return
        }
        _local.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val found = AddGiftLogic.findDuplicate(items, childId, state.title, links(state))
            if (found != null) {
                _local.update { it.copy(isSaving = false, duplicate = found) }
            } else {
                performSave()
            }
        }
    }

    /** Enregistre sans vérifier les doublons (l'utilisateur a confirmé, ou c'est une modification). */
    fun save() {
        if (!_local.value.canSave) return
        _local.update { it.copy(isSaving = true, error = null, duplicate = null) }
        viewModelScope.launch { performSave() }
    }

    private fun links(state: AddGiftUiState): List<DraftLink> =
        listOfNotNull(AddGiftLogic.mainLink(state.urlText, state.store, state.priceText, state.currency)) + state.otherLinks

    private suspend fun performSave() {
        val state = _local.value
        val failure = runCatchingCancellable {
            val userId = session.state.value.userId ?: error("NOT_AUTHENTICATED")
            val draft = GiftDraft(
                title = state.title, notes = state.notes, imageUrl = state.imageUrl, imageBytes = state.imageBytes,
                isFavorite = state.isFavorite, eventId = state.eventId, links = links(state), submitToParents = state.submitToParents,
            )
            var image = draft.imageUrl
            draft.imageBytes?.let { image = images.upload(it, userId) }
            val current = existing
            if (current != null) {
                items.updateItem(
                    id = current.id, title = draft.title.trim(), notes = AddGiftLogic.trimmedNotes(draft.notes), imageUrl = image,
                    priority = if (draft.isFavorite) 1 else 0, eventId = draft.eventId,
                )
                items.replaceLinks(current.id, draft.links)
            } else {
                val newId = items.addItem(AddGiftLogic.newItem(childId, kind, owned, draft, image, userId), draft.links)
                // Rien à annoncer pour un cadeau déjà possédé.
                if (!owned) notifier.notifyNewItems(listOf(newId))
            }
        }.exceptionOrNull()
        if (failure == null) session.itemsChanged()
        _local.update {
            if (failure == null) it.copy(isSaving = false, finished = true) else it.copy(isSaving = false, error = GiftError.from(failure))
        }
    }

    // endregion

    companion object {
        const val PREVIEW_DEBOUNCE_MS = 700L
    }
}
