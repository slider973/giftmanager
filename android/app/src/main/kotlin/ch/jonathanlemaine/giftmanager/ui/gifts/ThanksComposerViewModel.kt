package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.gifts.ThanksLogic
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.data.ImageUploader
import ch.jonathanlemaine.giftmanager.data.ItemNotifier
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import ch.jonathanlemaine.giftmanager.data.ThanksRepository
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Rédaction d'un merci (iOS : `ThanksComposerView`). */
data class ThanksComposerUiState(
    val isLoading: Boolean = true,
    val item: WishItem? = null,
    /** Prénom de l'enfant qui a reçu le cadeau (« Reçu par Léa »). */
    val childName: String = "",
    val message: String = "",
    /** Photo jointe, déjà redimensionnée en JPEG. */
    val photo: ByteArray? = null,
    val isSending: Boolean = false,
    val error: GiftError? = null,
    /** Le merci est parti : l'écran affiche « Merci envoyé ! ». */
    val sent: Boolean = false,
    /** Le cadeau n'existe plus : l'écran se ferme. */
    val isClosed: Boolean = false,
) {
    /** Longueur du message nettoyé, en points de code (compteur « 12 / 1000 »). */
    val length: Int get() = ThanksLogic.length(message)

    val isTooLong: Boolean get() = ThanksLogic.isTooLong(message)

    val canSend: Boolean get() = item != null && !sent && ThanksLogic.canSend(message, isSending)
}

class ThanksComposerViewModel(
    private val itemId: UUID,
    private val session: AppSession,
    items: ItemRepository,
    private val thanks: ThanksRepository,
    private val images: ImageUploader,
    private val notifier: ItemNotifier,
    clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val loader = ItemLoader(items, clock)

    private val _local = MutableStateFlow(ThanksComposerUiState())

    val uiState: StateFlow<ThanksComposerUiState> = combine(_local, session.state) { local, s -> derive(local, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, derive(_local.value, session.state.value))

    private fun derive(local: ThanksComposerUiState, s: SessionState) =
        local.copy(childName = local.item?.let { s.child(it.childId)?.firstName }.orEmpty())

    /** Dernière photo envoyée et son URL : un nouvel essai après un échec du merci ne la renvoie pas. */
    private var uploaded: Pair<ByteArray, String>? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        runCatchingCancellable { loader.load(itemId, session.state.value.events) }
            .onSuccess { loaded ->
                // Le message de départ n'est posé qu'une fois : le chargement ne se rejoue pas.
                _local.update {
                    it.copy(isLoading = false, item = loaded.item, message = ThanksLogic.defaultMessage(loaded.item.title))
                }
            }
            .onFailure { failure ->
                val error = GiftError.from(failure)
                _local.update { it.copy(isLoading = false, error = error, isClosed = error == GiftError.NotFound) }
            }
    }

    fun onMessageChange(message: String) = _local.update { it.copy(message = message) }

    fun onPhotoPicked(jpeg: ByteArray) = _local.update { it.copy(photo = jpeg) }

    fun onPhotoRemoved() = _local.update { it.copy(photo = null) }

    fun dismissError() = _local.update { it.copy(error = null) }

    /**
     * Envoie le merci. La photo part d'abord dans le stockage : le serveur n'accepte que l'URL publique d'une image
     * du bucket. Un second appui pendant l'envoi est ignoré.
     */
    fun send() {
        val state = _local.value
        val userId = session.state.value.userId
        val current = state.item
        if (current == null || userId == null || !uiState.value.canSend) return
        _local.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                val photoUrl = state.photo?.let { bytes ->
                    uploaded?.takeIf { it.first === bytes }?.second ?: images.upload(bytes, userId).also { uploaded = bytes to it }
                }
                thanks.sendThanks(current.id, ThanksLogic.trimmed(state.message), photoUrl)
            }
            result.onSuccess {
                // Fire-and-forget : un échec de la notification ne défait pas le merci.
                notifier.notifyThanks(current.id)
                _local.update { it.copy(isSending = false, sent = true) }
            }.onFailure { failure ->
                _local.update { it.copy(isSending = false, error = GiftError.from(failure)) }
            }
        }
    }
}
