package ch.jonathanlemaine.giftmanager.ui.children

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.AvatarPalette
import ch.jonathanlemaine.giftmanager.ui.common.DateField
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import java.time.LocalDate

/** Actions de [ChildEditorScreen], reliées à [ChildEditorViewModel]. */
data class ChildEditorActions(
    val onFirstNameChange: (String) -> Unit,
    val onHasBirthdateChange: (Boolean) -> Unit,
    val onBirthdateChange: (LocalDate) -> Unit,
    val onEmojiChange: (String) -> Unit,
    val onColorChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onDelete: () -> Unit,
    val onDismissError: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * Câblage minimal de l'éditeur d'enfant / de liste d'adulte (iOS : `ChildEditorView`), à remplacer par
 * l'écran définitif de l'`ui-designer`. Les champs et actions sont complets ; seul l'habillage est provisoire.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildEditorScreen(state: ChildEditorUiState, actions: ChildEditorActions, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    val title = state.savedFirstName
        ?: stringResource(if (state.isAdult) R.string.child_editor_title_new_adult else R.string.child_editor_title_new)
    ScreenColumn(modifier = modifier) {
        TextLinkButton(text = stringResource(R.string.cancel), onClick = actions.onClose)
        ScreenTitle(title)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            ChildAvatar(name = state.firstName.ifEmpty { "?" }, emoji = state.emoji, colorName = state.colorName, size = 96.dp)
        }
        FCTextField(
            label = stringResource(if (state.isAdult) R.string.child_name_adult else R.string.child_name),
            value = state.firstName,
            onValueChange = actions.onFirstNameChange,
            leadingIcon = GiftIcons.Person,
            placeholder = stringResource(if (state.isAdult) R.string.child_name_prompt_adult else R.string.child_name_prompt),
        )
        if (state.isAdult) FCNotice(GiftIcons.EyeSlash, stringResource(R.string.child_adult_notice), tone = FCNoticeTone.SURPRISE)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            Text(stringResource(R.string.child_birthdate_toggle), color = GiftTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Switch(checked = state.hasBirthdate, onCheckedChange = actions.onHasBirthdateChange)
        }
        if (state.hasBirthdate) {
            DateField(
                label = stringResource(if (state.isAdult) R.string.child_birthdate_label_adult else R.string.child_birthdate_label),
                date = state.birthdate,
                onDateChange = actions.onBirthdateChange,
                max = state.maxBirthdate,
            )
        }
        Text(stringResource(R.string.child_avatar_title), style = GiftTheme.type.headline, color = GiftTheme.colors.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            AvatarPalette.emojis.forEach { emoji ->
                val selected = emoji == state.emoji
                val description = stringResource(R.string.child_avatar_a11y, emoji)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .border(2.dp, if (selected) GiftTheme.colors.primary else GiftTheme.colors.separator, CircleShape)
                        .clickable { actions.onEmojiChange(emoji) }
                        .semantics { contentDescription = description; role = Role.RadioButton; this.selected = selected },
                ) { Text(emoji) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            AvatarPalette.colors.forEach { name ->
                val selected = name == state.colorName
                val description = stringResource(R.string.child_color_a11y, colorLabel(name))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(GiftTheme.colors.pastel(name) ?: GiftTheme.colors.pastelMint, CircleShape)
                        .border(if (selected) 3.dp else 1.dp, if (selected) GiftTheme.colors.primary else GiftTheme.colors.separator, CircleShape)
                        .clickable { actions.onColorChange(name) }
                        .semantics { contentDescription = description; role = Role.RadioButton; this.selected = selected },
                )
            }
        }
        PrimaryButton(
            text = stringResource(
                when {
                    state.isEditing -> R.string.child_save_edit
                    state.isAdult -> R.string.child_save_create_adult
                    else -> R.string.child_save_create
                },
            ),
            onClick = actions.onSave,
            leadingIcon = GiftIcons.Checkmark,
            isLoading = state.isSaving,
            enabled = state.canSave,
        )
        if (state.isEditing) {
            TextLinkButton(text = stringResource(R.string.child_delete, state.savedFirstName.orEmpty()), onClick = actions.onRequestDelete)
        }
    }
    if (state.confirmDelete) {
        GiftAlertDialog(
            title = stringResource(R.string.child_delete_title, state.savedFirstName.orEmpty()),
            message = stringResource(R.string.child_delete_message),
            confirmLabel = stringResource(if (state.isAdult) R.string.child_delete_confirm_adult else R.string.child_delete_confirm_child),
            onConfirm = actions.onDelete,
            onDismiss = actions.onCancelDelete,
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

@Composable
private fun colorLabel(name: String): String = stringResource(
    when (name) {
        "pastelPink" -> R.string.child_color_pink
        "pastelMint" -> R.string.child_color_mint
        "pastelBlue" -> R.string.child_color_blue
        "pastelPeach" -> R.string.child_color_peach
        else -> R.string.child_color_lavender
    },
)
