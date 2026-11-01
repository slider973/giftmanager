package ch.jonathanlemaine.giftmanager.ui.budgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import java.util.UUID

/** Actions de [BudgetEditorScreen], reliées à [BudgetEditorViewModel]. */
data class BudgetEditorActions(
    val onChildChange: (UUID?) -> Unit,
    val onEventChange: (UUID?) -> Unit,
    val onAmountChange: (String) -> Unit,
    val onCurrencyChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onDelete: () -> Unit,
    val onDismissError: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * Câblage minimal de l'éditeur de budget (iOS : `BudgetEditorView`), formulaire plein écran : enfant, événement,
 * montant, devise, enregistrer, supprimer. Mise en forme définitive à reprendre par le `ui-designer`.
 */
@Composable
fun BudgetEditorScreen(state: BudgetEditorUiState, actions: BudgetEditorActions, modifier: Modifier = Modifier) {
    val type = GiftTheme.type
    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.CLOSE,
                onNavigate = actions.onClose,
                title = stringResource(if (state.isEditing) R.string.budget_editor_title_edit else R.string.budget_editor_title_new),
            )
        },
    ) {
        ScreenColumn {
            Text(stringResource(R.string.budget_child_label), style = type.captionBold)
            Column {
                FilterChip(
                    selected = state.childId == null,
                    onClick = { actions.onChildChange(null) },
                    label = { Text(stringResource(R.string.budget_all_children)) },
                )
                state.children.forEach { child ->
                    FilterChip(
                        selected = state.childId == child.id,
                        onClick = { actions.onChildChange(child.id) },
                        label = { Text(child.firstName) },
                    )
                }
            }
            Text(stringResource(R.string.budget_event_label), style = type.captionBold)
            Column {
                FilterChip(
                    selected = state.eventId == null,
                    onClick = { actions.onEventChange(null) },
                    label = { Text(stringResource(R.string.budget_all_events)) },
                )
                state.events.forEach { event ->
                    FilterChip(
                        selected = state.eventId == event.id,
                        onClick = { actions.onEventChange(event.id) },
                        label = { Text(event.title) },
                    )
                }
            }
            if (!state.hasScope) Text(stringResource(R.string.budget_scope_hint), style = type.caption)

            FCTextField(
                label = stringResource(R.string.budget_amount_label),
                value = state.amountText,
                onValueChange = actions.onAmountChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Text(stringResource(R.string.budget_currency_label), style = type.captionBold)
            Column {
                state.currencies.forEach { code ->
                    FilterChip(selected = state.currency == code, onClick = { actions.onCurrencyChange(code) }, label = { Text(code) })
                }
            }
            Text(stringResource(R.string.budget_currency_hint, state.currency), style = type.caption)
            Text(stringResource(R.string.budget_private_note), style = type.caption)

            PrimaryButton(
                text = stringResource(if (state.isEditing) R.string.budget_save else R.string.budget_create),
                onClick = actions.onSave,
                isLoading = state.isSaving,
                enabled = state.canSave,
            )
            if (state.isEditing) TextLinkButton(stringResource(R.string.budget_delete), actions.onRequestDelete, enabled = !state.isSaving)
        }
    }

    if (state.confirmDelete) {
        GiftAlertDialog(
            title = stringResource(R.string.budget_delete_title),
            message = stringResource(R.string.budget_delete_message),
            confirmLabel = stringResource(R.string.budget_delete_confirm),
            onConfirm = actions.onDelete,
            onDismiss = actions.onCancelDelete,
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}
