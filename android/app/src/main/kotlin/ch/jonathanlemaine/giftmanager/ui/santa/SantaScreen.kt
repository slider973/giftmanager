package ch.jonathanlemaine.giftmanager.ui.santa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession.BlockReason
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession.Stage
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground

/** Rappels de la session du Père Noël. */
data class SantaActions(
    val onClose: () -> Unit,
    val onSkipSpeaking: () -> Unit,
    val onReplay: () -> Unit,
    val onSubmitAnswer: (String) -> Unit,
    val onSkipAnswer: () -> Unit,
    val onToggleActivity: (String) -> Unit,
    val onApprove: (SantaSession.Suggestion) -> Unit,
    val onReject: (SantaSession.Suggestion) -> Unit,
    val onFinish: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Câblage minimal de la session (iOS : `SantaSessionView` + `SantaSuggestionsView`), en attendant le design : la
 * réplique et sa saisie pendant le dialogue, puis la revue des suggestions, puis le bilan.
 */
@Composable
fun SantaScreen(state: SantaUiState, actions: SantaActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fcScreenBackground()
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.santa_title, state.childName))
        when (val stage = state.stage) {
            Stage.NotStarted -> Unit
            is Stage.Speaking, is Stage.AwaitingAnswer -> Dialogue(state, stage, actions)
            Stage.ReviewingSuggestions -> Suggestions(state, actions)
            Stage.Done -> {
                Text(
                    if (state.savedCount > 0) stringResource(R.string.santa_done_saved, state.savedCount)
                    else stringResource(R.string.santa_done_none),
                )
                Button(onClick = actions.onClose) { Text(stringResource(R.string.santa_close)) }
            }
            is Stage.Blocked -> {
                Text(stringResource(stage.reason.messageRes()))
                Button(onClick = actions.onClose) { Text(stringResource(R.string.santa_close)) }
            }
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

private fun BlockReason.messageRes(): Int = when (this) {
    BlockReason.ChildMode -> R.string.santa_blocked_child_mode
    BlockReason.EmptyScript -> R.string.santa_blocked_empty_script
    BlockReason.NoStore -> R.string.santa_blocked_no_store
    BlockReason.NoWish -> R.string.santa_blocked_no_wish
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dialogue(state: SantaUiState, stage: Stage, actions: SantaActions) {
    val line = state.currentLine ?: return
    Text(line.texte)
    if (state.audioUnavailable) Text(stringResource(R.string.santa_audio_unavailable))
    if (stage is Stage.Speaking) {
        Text(stringResource(R.string.santa_speaking))
        TextButton(onClick = actions.onSkipSpeaking) { Text(stringResource(R.string.santa_skip_speaking)) }
        return
    }
    TextButton(onClick = actions.onReplay) { Text(stringResource(R.string.santa_replay)) }
    line.aideParent?.let { Text(it) }

    val choices = line.choix.orEmpty()
    var answer by rememberSaveable(line.id) { mutableStateOf("") }
    if (choices.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                FilterChip(
                    selected = choice.cle in state.wishes.activites,
                    onClick = { actions.onToggleActivity(choice.cle) },
                    label = { Text(choice.libelle) },
                )
            }
        }
    } else {
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text(stringResource(R.string.santa_answer_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(onClick = { actions.onSubmitAnswer(answer) }) { Text(stringResource(R.string.santa_answer_send)) }
    if (line.facultatif) TextButton(onClick = actions.onSkipAnswer) { Text(stringResource(R.string.santa_answer_skip)) }
}

@Composable
private fun Suggestions(state: SantaUiState, actions: SantaActions) {
    Text(stringResource(R.string.santa_suggestions_title))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        items(state.suggestions, key = { it.id }) { suggestion ->
            Column {
                Text(suggestion.query)
                Text(stringResource(R.string.santa_suggestion_store, suggestion.storeName))
                Column {
                    Button(onClick = { actions.onApprove(suggestion) }, enabled = state.savingId == null) {
                        Text(stringResource(R.string.santa_suggestion_approve))
                    }
                    TextButton(onClick = { actions.onReject(suggestion) }) { Text(stringResource(R.string.santa_suggestion_reject)) }
                }
            }
        }
    }
    Button(onClick = actions.onFinish) { Text(stringResource(R.string.santa_finish)) }
}
