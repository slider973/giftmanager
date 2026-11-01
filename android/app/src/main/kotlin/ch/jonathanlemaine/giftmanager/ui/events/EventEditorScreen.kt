package ch.jonathanlemaine.giftmanager.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.DateField
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.SettingsActionRow
import ch.jonathanlemaine.giftmanager.ui.common.SettingsSection
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.time.LocalDate

/** Actions de [EventEditorScreen], reliées à [EventEditorViewModel]. */
data class EventEditorActions(
    val onTitleChange: (String) -> Unit,
    val onDateChange: (LocalDate) -> Unit,
    val onSave: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onDelete: () -> Unit,
    val onDismissError: () -> Unit,
    val onClose: () -> Unit,
)

/** Le compteur de caractères n'apparaît qu'à l'approche de la limite (80 % de 80). */
private const val COUNTER_THRESHOLD = 64

/**
 * Création et modification d'un événement (iOS : `EventEditorView`). Formulaire plein écran (sans barre
 * d'onglets) : ✕ « Annuler » + « Nouvel événement » / « Modifier », titre (80 caractères, compteur près de
 * la limite), date, « Enregistrer », puis la suppression dans sa propre carte avec sa note « Les cadeaux
 * restent dans les listes des enfants » et une confirmation.
 */
@Composable
fun EventEditorScreen(state: EventEditorUiState, actions: EventEditorActions, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    val focusManager = LocalFocusManager.current
    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.CLOSE,
                onNavigate = actions.onClose,
                title = stringResource(if (state.isEditing) R.string.event_editor_title_edit else R.string.event_editor_title_new),
            )
        },
    ) {
        ScreenColumn {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                FCTextField(
                    label = stringResource(R.string.event_title_label),
                    value = state.title,
                    onValueChange = actions.onTitleChange,
                    leadingIcon = GiftIcons.Calendar,
                    placeholder = stringResource(R.string.event_title_prompt),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                )
                TitleCounter(state.title.length)
            }
            DateField(label = stringResource(R.string.event_date_label), date = state.date, onDateChange = actions.onDateChange)
            PrimaryButton(
                text = stringResource(R.string.event_save),
                onClick = {
                    focusManager.clearFocus()
                    actions.onSave()
                },
                leadingIcon = GiftIcons.Checkmark,
                isLoading = state.isSaving,
                enabled = state.canSave,
                modifier = Modifier.padding(top = spacing.s),
            )
            if (state.canDelete) {
                SettingsSection(
                    title = null,
                    footer = stringResource(R.string.event_delete_footer),
                    modifier = Modifier.padding(top = spacing.m),
                ) {
                    SettingsActionRow(
                        text = stringResource(R.string.event_delete),
                        onClick = actions.onRequestDelete,
                        icon = GiftIcons.Delete,
                        color = GiftTheme.colors.takenFg,
                    )
                }
            }
        }
    }
    if (state.confirmDelete) {
        GiftAlertDialog(
            title = stringResource(R.string.event_delete_title),
            confirmLabel = stringResource(R.string.event_delete_confirm),
            onConfirm = actions.onDelete,
            onDismiss = actions.onCancelDelete,
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

/** « 72 / 80 », aligné à droite sous le champ ; `potFg` à la limite (jamais l'ambre en texte, cf. DESIGN.md). */
@Composable
private fun TitleCounter(length: Int) {
    if (length < COUNTER_THRESHOLD) return
    val max = EventEditorUiState.MAX_TITLE_LENGTH
    val description = pluralStringResource(R.plurals.event_title_counter_a11y, length, length, max)
    val atLimit = length >= max
    Text(
        text = stringResource(R.string.event_title_counter, length, max),
        style = GiftTheme.type.caption.tabular().let { if (atLimit) it.copy(fontWeight = FontWeight.SemiBold) else it },
        color = if (atLimit) GiftTheme.colors.potFg else GiftTheme.colors.textSecondary,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = GiftTheme.spacing.xs)
            .clearAndSetSemantics { contentDescription = description },
    )
}

// region Aperçus

private val previewActions = EventEditorActions({}, {}, {}, {}, {}, {}, {}, {})

@GiftPreviews
@Composable
private fun EventEditorCreatePreview() = ScreenPreviewFrame {
    EventEditorScreen(EventEditorUiState(date = LocalDate.of(2026, 6, 20)), previewActions)
}

@GiftPreviews
@Composable
private fun EventEditorEditPreview() = ScreenPreviewFrame(height = 900.dp) {
    EventEditorScreen(
        EventEditorUiState(
            isEditing = true,
            title = "Grande fête de fin d'année chez Mamie et Papi, avec toute la famille",
            date = LocalDate.of(2026, 6, 20),
            canDelete = true,
        ),
        previewActions,
    )
}

@GiftPreviews
@Composable
private fun EventEditorDeletePreview() = ScreenPreviewFrame {
    EventEditorScreen(
        EventEditorUiState(isEditing = true, title = "Fête de fin d'année", date = LocalDate.of(2026, 6, 20), canDelete = true, confirmDelete = true),
        previewActions,
    )
}

// endregion
