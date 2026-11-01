package ch.jonathanlemaine.giftmanager.ui.santa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLock
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.santa.SantaScript
import ch.jonathanlemaine.giftmanager.core.santa.SantaScriptSource
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession.Stage
import ch.jonathanlemaine.giftmanager.core.santa.SantaSessionActions
import ch.jonathanlemaine.giftmanager.core.santa.SantaVoicePlayer
import ch.jonathanlemaine.giftmanager.core.santa.SantaWishes
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.core.session.household
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Session du Père Noël telle que l'écran l'affiche (iOS : `SantaSessionView` + `SantaSuggestionsView`). */
data class SantaUiState(
    val childName: String = "",
    val stage: Stage = Stage.NotStarted,
    /** Réplique dite ou en attente de réponse ; `null` hors de ces deux étapes. */
    val currentLine: SantaScript.Line? = null,
    val wishes: SantaWishes = SantaWishes(),
    val suggestions: List<SantaSession.Suggestion> = emptyList(),
    /** Idées enregistrées par le parent pendant cette session. */
    val savedCount: Int = 0,
    val isPlaying: Boolean = false,
    /** L'audio manquait ou n'a pas pu être lu : l'écran montre le texte seul, le parent le lit. */
    val audioUnavailable: Boolean = false,
    /** Suggestion en cours d'enregistrement : un second appui est ignoré jusqu'à la réponse. */
    val savingId: UUID? = null,
    val error: GiftError? = null,
)

/**
 * Enveloppe une [SantaSession] dans un état observable et enchaîne l'audio : chaque réplique dite est jouée, puis la
 * session avance. L'état vit ici (et non dans l'écran) : une rotation ne relance pas la lecture.
 *
 * Le pays des boutiques est celui du foyer de l'enfant, jamais la locale de l'appareil ; le mode enfant est lu sur
 * [ChildModeLock] au démarrage.
 */
class SantaViewModel(
    private val childId: UUID,
    appSession: AppSession,
    private val actions: SantaSessionActions,
    scriptSource: SantaScriptSource,
    private val player: SantaVoicePlayer,
    lock: ChildModeLock,
) : ViewModel() {
    private val childName: String
    private val santa: SantaSession

    private val _uiState = MutableStateFlow(SantaUiState())
    val uiState: StateFlow<SantaUiState> = _uiState.asStateFlow()

    /** Réplique dont la lecture est en cours ; `null` si rien ne joue (arrêt à la mise en arrière-plan, par exemple). */
    private var playingLine: Int? = null

    init {
        val state = appSession.state.value
        val child = state.child(childId)
        childName = child?.firstName.orEmpty()
        santa = SantaSession(
            script = scriptSource.load(),
            householdCountry = child?.let { state.household(of = it)?.country },
            childModeActive = lock.active.value,
        )
        // Pause du lecteur sans fin de réplique (perte de focus audio) : l'état affiché suit.
        player.onStateChanged = ::publish
        santa.start()
        publish()
        playIfSpeaking()
    }

    private fun publish() = _uiState.update {
        SantaUiState(
            childName = childName,
            stage = santa.stage,
            currentLine = santa.currentLine,
            wishes = santa.wishes,
            suggestions = santa.suggestions,
            savedCount = santa.savedCount,
            isPlaying = player.isPlaying,
            audioUnavailable = player.audioUnavailable,
            savingId = it.savingId,
            error = it.error,
        )
    }

    // region Voix

    private fun playIfSpeaking() {
        val stage = santa.stage as? Stage.Speaking
        val line = santa.currentLine
        if (stage == null || line == null) {
            playingLine = null
            return
        }
        val index = stage.lineIndex
        playingLine = index
        // `onFinish` peut arriver tout de suite (audio introuvable) : la session avance quand même.
        player.play(line.audioAsset) { onSpeechFinished(index) }
        publish()
    }

    private fun onSpeechFinished(index: Int) {
        // Un rappel tardif d'une réplique déjà passée (ou sautée) ne doit rien avancer.
        if (santa.stage != Stage.Speaking(index)) return
        playingLine = null
        santa.advanceAfterSpeaking()
        publish()
        playIfSpeaking()
    }

    /** Le parent passe la voix et lit la réplique lui-même. */
    fun skipSpeaking() {
        val stage = santa.stage as? Stage.Speaking ?: return
        player.stop()
        onSpeechFinished(stage.lineIndex)
    }

    /** Rejoue la réplique courante. */
    fun replay() {
        val line = santa.currentLine ?: return
        player.stop()
        playingLine = null
        if (santa.stage is Stage.Speaking) {
            playIfSpeaking()
        } else {
            // En attente de réponse : la réplique rejouée n'avance rien.
            player.play(line.audioAsset) { publish() }
            publish()
        }
    }

    /**
     * L'écran quitte le premier plan : la voix s'arrête. Pendant un changement de configuration (rotation) l'activité
     * est recréée mais le ViewModel survit : la voix continue, sans coupure ni reprise.
     */
    fun onScreenStopped(changingConfigurations: Boolean = false) {
        if (changingConfigurations) return
        player.stop()
        playingLine = null
        publish()
    }

    /** L'écran revient au premier plan : une réplique interrompue reprend. */
    fun onScreenStarted() {
        val stage = santa.stage as? Stage.Speaking ?: return
        if (playingLine != stage.lineIndex) playIfSpeaking()
    }

    // endregion

    // region Réponses du parent

    fun submitAnswer(value: String) {
        if (santa.stage !is Stage.AwaitingAnswer) return
        player.stop()
        santa.submitAnswer(value)
        publish()
        playIfSpeaking()
    }

    fun skipAnswer() {
        if (santa.stage !is Stage.AwaitingAnswer) return
        player.stop()
        santa.skipAnswer()
        publish()
        playIfSpeaking()
    }

    fun toggleActivity(key: String) {
        santa.toggleActivity(key)
        publish()
    }

    // endregion

    // region Suggestions

    /** Le parent retient une suggestion : elle devient une idée de cadeau. En cas d'échec elle reste, pour réessayer. */
    fun approve(suggestion: SantaSession.Suggestion) {
        if (_uiState.value.savingId != null || santa.suggestions.none { it.id == suggestion.id }) return
        _uiState.update { it.copy(savingId = suggestion.id, error = null) }
        viewModelScope.launch {
            runCatchingCancellable { actions.createGiftIdea(childId, suggestion.query, suggestion.url) }
                .onSuccess {
                    santa.markSaved(suggestion)
                    _uiState.update { it.copy(savingId = null) }
                    publish()
                }
                .onFailure { failure -> _uiState.update { it.copy(savingId = null, error = GiftError.from(failure)) } }
        }
    }

    /** Le parent écarte une suggestion sans rien enregistrer. */
    fun reject(suggestion: SantaSession.Suggestion) {
        santa.reject(suggestion)
        publish()
    }

    /** Clôt la session en laissant tomber ce qui n'a pas été validé. */
    fun finish() {
        player.stop()
        playingLine = null
        santa.finish()
        publish()
    }

    // endregion

    fun dismissError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        player.onStateChanged = null
        player.stop()
        player.release()
    }
}
