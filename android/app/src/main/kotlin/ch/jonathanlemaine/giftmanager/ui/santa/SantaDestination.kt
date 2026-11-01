package ch.jonathanlemaine.giftmanager.ui.santa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Session du Père Noël (#78) : plein écran, lancée depuis la liste d'un enfant. La voix s'arrête quand l'app quitte le
 * premier plan et reprend à son retour.
 */
@Composable
fun SantaDestination(viewModelFactory: ViewModelProvider.Factory, onClose: () -> Unit) {
    val viewModel: SantaViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onScreenStarted() }
    SantaScreen(
        state = state,
        actions = SantaActions(
            onClose = onClose,
            onSkipSpeaking = viewModel::skipSpeaking,
            onReplay = viewModel::replay,
            onSubmitAnswer = viewModel::submitAnswer,
            onSkipAnswer = viewModel::skipAnswer,
            onToggleActivity = viewModel::toggleActivity,
            onApprove = viewModel::approve,
            onReject = viewModel::reject,
            onFinish = viewModel::finish,
            onDismissError = viewModel::dismissError,
        ),
    )
}
