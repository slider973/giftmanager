package ch.jonathanlemaine.giftmanager.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.DateField
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
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

/** Câblage minimal de l'éditeur d'événement (iOS : `EventEditorView`), à remplacer par l'écran de l'`ui-designer`. */
@Composable
fun EventEditorScreen(state: EventEditorUiState, actions: EventEditorActions, modifier: Modifier = Modifier) {
    ScreenColumn(modifier = modifier) {
        TextLinkButton(text = stringResource(R.string.cancel), onClick = actions.onClose)
        ScreenTitle(stringResource(if (state.isEditing) R.string.event_editor_title_edit else R.string.event_editor_title_new))
        FCTextField(
            label = stringResource(R.string.event_title_label),
            value = state.title,
            onValueChange = actions.onTitleChange,
            leadingIcon = GiftIcons.Calendar,
            placeholder = stringResource(R.string.event_title_prompt),
        )
        DateField(label = stringResource(R.string.event_date_label), date = state.date, onDateChange = actions.onDateChange)
        PrimaryButton(
            text = stringResource(R.string.event_save),
            onClick = actions.onSave,
            leadingIcon = GiftIcons.Checkmark,
            isLoading = state.isSaving,
            enabled = state.canSave,
        )
        if (state.canDelete) {
            TextLinkButton(text = stringResource(R.string.event_delete), onClick = actions.onRequestDelete)
            Text(stringResource(R.string.event_delete_footer), style = GiftTheme.type.caption, color = GiftTheme.colors.textSecondary)
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
