package ch.jonathanlemaine.giftmanager.ui.budgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.PickerOption
import ch.jonathanlemaine.giftmanager.ui.common.PickerRow
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.SettingsActionRow
import ch.jonathanlemaine.giftmanager.ui.common.SettingsDivider
import ch.jonathanlemaine.giftmanager.ui.common.SettingsSection
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.gifts.FieldLabel
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftPreviewData
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.math.BigDecimal
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

/** Valeur d'un menu de périmètre pour « Tous les enfants » / « Tous les événements ». */
private const val ALL = ""

/**
 * Définir, modifier ou supprimer un budget privé (iOS : `BudgetEditorView`), formulaire plein écran (✕ Annuler).
 *
 * Carte de périmètre (enfant, événement : menus déroulants, « Tous… » = aucun ; au moins l'un des deux), carte
 * « Montant » (champ `title` tabulaire + devise en menu, rappel de la devise comptée), mention de confidentialité,
 * « Créer le budget » / « Enregistrer », et « Supprimer ce budget » dans sa propre carte, avec confirmation.
 */
@Composable
fun BudgetEditorScreen(state: BudgetEditorUiState, actions: BudgetEditorActions, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    val focusManager = LocalFocusManager.current
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
        ScreenColumn(spacing = spacing.xl) {
            ScopeCard(state, actions)
            AmountCard(state, actions)
            FCNotice(GiftIcons.Lock, stringResource(R.string.budget_private_note))
            PrimaryButton(
                text = stringResource(if (state.isEditing) R.string.budget_save else R.string.budget_create),
                onClick = {
                    focusManager.clearFocus()
                    actions.onSave()
                },
                leadingIcon = GiftIcons.Checkmark,
                isLoading = state.isSaving,
                enabled = state.canSave,
            )
            if (state.isEditing) {
                SettingsSection(title = null, modifier = Modifier.padding(top = spacing.s)) {
                    SettingsActionRow(
                        text = stringResource(R.string.budget_delete),
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

/** Enfant et événement en lignes de choix (`PickerRow`), puis le rappel « au moins l'un des deux » tant qu'il manque. */
@Composable
private fun ScopeCard(state: BudgetEditorUiState, actions: BudgetEditorActions) {
    val allChildren = stringResource(R.string.budget_all_children)
    val allEvents = stringResource(R.string.budget_all_events)
    val childOptions = listOf(PickerOption(ALL, allChildren)) + state.children.map { PickerOption(it.id.toString(), it.firstName) }
    val eventOptions = listOf(PickerOption(ALL, allEvents)) + state.events.map { PickerOption(it.id.toString(), it.title) }
    val childValue = state.childId?.toString() ?: ALL
    val eventValue = state.eventId?.toString() ?: ALL
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
        SettingsSection(title = null) {
            PickerRow(
                icon = GiftIcons.Person,
                label = stringResource(R.string.budget_child_label),
                selectedText = childOptions.firstOrNull { it.value == childValue }?.text ?: allChildren,
                options = childOptions,
                selectedValue = childValue,
                onSelect = { value -> actions.onChildChange(value.takeIf { it != ALL }?.let(UUID::fromString)) },
            )
            SettingsDivider()
            PickerRow(
                icon = GiftIcons.Calendar,
                label = stringResource(R.string.budget_event_label),
                selectedText = eventOptions.firstOrNull { it.value == eventValue }?.text ?: allEvents,
                options = eventOptions,
                selectedValue = eventValue,
                onSelect = { value -> actions.onEventChange(value.takeIf { it != ALL }?.let(UUID::fromString)) },
            )
        }
        AnimatedVisibility(visible = !state.hasScope, enter = fadeIn(tween(150)), exit = fadeOut(tween(100))) {
            val type = GiftTheme.type
            val colors = GiftTheme.colors
            Row(
                horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = GiftTheme.spacing.l).semantics(mergeDescendants = true) { },
            ) {
                Icon(GiftIcons.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(type.caption.glyphSize()))
                Text(stringResource(R.string.budget_scope_hint), style = type.caption, color = colors.textSecondary)
            }
        }
    }
}

/**
 * Montant en `title` tabulaire dans un champ `background` bordé (`primary` 2 dp au focus, `takenFg` + message si
 * illisible), devise en pastille-menu à droite, puis « Seuls les cadeaux dont un lien est en CHF… ». Le champ
 * prend le focus à la création (iOS : `amountFocused`).
 */
@Composable
private fun AmountCard(state: BudgetEditorUiState, actions: BudgetEditorActions) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val label = stringResource(R.string.budget_amount_label)
    val invalidText = stringResource(R.string.budget_amount_invalid)
    val emptyText = stringResource(R.string.join_pot_amount_empty)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val inPreview = LocalInspectionMode.current
    LaunchedEffect(Unit) { if (!state.isEditing && !inPreview) focusRequester.requestFocus() }
    val borderColor = when {
        state.showsInvalid -> colors.takenFg
        focused -> colors.primary
        else -> colors.separator
    }
    val shape = GiftTheme.radius.fieldShape

    Column(verticalArrangement = Arrangement.spacedBy(spacing.s), modifier = Modifier.fillMaxWidth().fcCard()) {
        FieldLabel(label, Modifier.clearAndSetSemantics { })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HitTarget.button)
                .background(colors.background, shape)
                .border(if (focused || state.showsInvalid) 2.dp else 1.dp, borderColor, shape)
                .padding(start = spacing.m, end = spacing.xs),
        ) {
            val textStyle = type.title.tabular().copy(color = colors.textPrimary)
            BasicTextField(
                value = state.amountText,
                onValueChange = actions.onAmountChange,
                textStyle = textStyle,
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                interactionSource = source,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = spacing.s)
                    .focusRequester(focusRequester)
                    .semantics {
                        contentDescription = label
                        stateDescription = if (state.amountText.isEmpty()) emptyText else "${state.amountText} ${state.currency}"
                        if (state.showsInvalid) error(invalidText)
                    },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.amountText.isEmpty()) {
                            Text(stringResource(R.string.budget_amount_hint), style = textStyle, color = colors.textSecondary)
                        }
                        inner()
                    }
                },
            )
            CurrencyMenu(state.currency, state.currencies, actions.onCurrencyChange)
        }
        AnimatedVisibility(visible = state.showsInvalid, enter = fadeIn(tween(150)), exit = fadeOut(tween(100))) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(GiftIcons.Error, contentDescription = null, tint = colors.takenFg, modifier = Modifier.size(type.caption.glyphSize()))
                Text(invalidText, style = type.caption, color = colors.takenFg)
            }
        }
        Text(stringResource(R.string.budget_currency_hint, state.currency), style = type.caption, color = colors.textSecondary)
    }
}

/** Devise : pastille `surface` « CHF ▾ » `primary` (cible 48 dp, liste déroulante) ; option courante cochée. */
@Composable
private fun CurrencyMenu(currency: String, currencies: List<String>, onSelect: (String) -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.budget_currency_label)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
            modifier = Modifier
                .heightIn(min = HitTarget.minimum)
                .fcClickable(onClick = { open = true }, role = Role.DropdownList, shape = CapsuleShape)
                .background(colors.surface, CapsuleShape)
                .padding(start = GiftTheme.spacing.m, end = GiftTheme.spacing.s)
                .semantics {
                    contentDescription = label
                    stateDescription = currency
                },
        ) {
            Text(currency, style = type.headline, color = colors.primary)
            Icon(GiftIcons.DropDown, contentDescription = null, tint = colors.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = GiftTheme.radius.fieldShape, containerColor = colors.surface) {
            currencies.forEach { code ->
                val isSelected = code == currency
                DropdownMenuItem(
                    text = {
                        Text(code, style = if (isSelected) type.callout.copy(fontWeight = FontWeight.SemiBold) else type.callout, color = colors.textPrimary)
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(GiftIcons.Checkmark, contentDescription = null, tint = colors.secondary) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(code)
                    },
                    modifier = Modifier.heightIn(min = HitTarget.minimum).semantics { selected = isSelected },
                )
            }
        }
    }
}

// region Aperçus

private val previewActions = BudgetEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

private val previewBase = BudgetEditorUiState(
    children = listOf(GiftPreviewData.leo, GiftPreviewData.zoe),
    events = listOf(GiftPreviewData.christmas, GiftPreviewData.birthday),
    currency = "CHF",
)

@GiftPreviews
@Composable
private fun BudgetEditorNewPreview() = ScreenPreviewFrame(height = 900.dp) {
    BudgetEditorScreen(previewBase, previewActions)
}

@GiftPreviews
@Composable
private fun BudgetEditorEditPreview() = ScreenPreviewFrame(height = 900.dp) {
    BudgetEditorScreen(
        previewBase.copy(
            isEditing = true,
            childId = GiftPreviewData.leo.id,
            eventId = GiftPreviewData.christmas.id,
            amountText = "200",
            amount = BigDecimal(200),
        ),
        previewActions,
    )
}

@GiftPreviews
@Composable
private fun BudgetEditorInvalidPreview() = ScreenPreviewFrame(height = 900.dp) {
    BudgetEditorScreen(previewBase.copy(childId = GiftPreviewData.zoe.id, amountText = "abc", showsInvalid = true), previewActions)
}

@GiftPreviews
@Composable
private fun BudgetEditorDeletePreview() = ScreenPreviewFrame(height = 900.dp) {
    BudgetEditorScreen(
        previewBase.copy(isEditing = true, childId = GiftPreviewData.leo.id, amountText = "200", amount = BigDecimal(200), confirmDelete = true),
        previewActions,
    )
}

// endregion
