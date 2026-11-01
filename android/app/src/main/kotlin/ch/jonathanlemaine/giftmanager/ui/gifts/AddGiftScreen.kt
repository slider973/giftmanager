package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Rappels du formulaire d'ajout ou de modification d'un cadeau. */
data class AddGiftActions(
    val onClose: () -> Unit,
    val onUrlChange: (String) -> Unit,
    val onUrlSubmit: () -> Unit,
    val onTitleChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onPriceChange: (String) -> Unit,
    val onFavoriteChange: (Boolean) -> Unit,
    val onSubmitToParentsChange: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onConfirmDuplicate: () -> Unit,
    val onDismissDuplicate: () -> Unit,
    val onDismissError: () -> Unit,
)

/** Câblage minimal du formulaire : champs, interrupteurs, alerte de doublon (#61) et erreur. */
@Composable
fun AddGiftScreen(state: AddGiftUiState, actions: AddGiftActions, modifier: Modifier = Modifier) {
    val title = stringResource(
        when {
            state.isEditing -> R.string.add_gift_title_edit
            state.kind == WishKind.IDEA -> R.string.add_gift_title_idea
            else -> R.string.add_gift_title_new
        },
    )
    TopBarScreen(
        modifier = modifier,
        topBar = { GiftTopBar(TopBarNavigation.CLOSE, onNavigate = actions.onClose, title = title) },
    ) {
        ScreenColumn {
            FCTextField(
                label = stringResource(R.string.add_gift_url),
                value = state.urlText,
                onValueChange = actions.onUrlChange,
                leadingIcon = GiftIcons.Link,
                placeholder = stringResource(R.string.add_gift_url_placeholder),
            )
            if (state.isFetching) Text(stringResource(R.string.add_gift_fetching), color = GiftTheme.colors.textSecondary)
            if (state.previewFailed) {
                FCNotice(GiftIcons.Warning, stringResource(R.string.add_gift_preview_failed), tone = FCNoticeTone.WARNING)
            }
            FCTextField(stringResource(R.string.add_gift_name), state.title, actions.onTitleChange)
            FCTextField(stringResource(R.string.add_gift_price), state.priceText, actions.onPriceChange)
            FCTextField(stringResource(R.string.add_gift_notes), state.notes, actions.onNotesChange, singleLine = false)
            if (state.showsSubmitToParents) {
                Text(stringResource(R.string.add_gift_submit_to_parents), color = GiftTheme.colors.textPrimary)
                Switch(checked = state.submitToParents, onCheckedChange = actions.onSubmitToParentsChange)
            }
            if (state.showsFavorite) {
                Text(stringResource(R.string.add_gift_favorite), color = GiftTheme.colors.textPrimary)
                Switch(checked = state.isFavorite, onCheckedChange = actions.onFavoriteChange)
            }
            PrimaryButton(
                text = stringResource(
                    when {
                        state.isEditing -> R.string.add_gift_save_edit
                        state.kind == WishKind.IDEA -> R.string.add_gift_save_idea
                        else -> R.string.add_gift_save_new
                    },
                ),
                onClick = actions.onSave,
                isLoading = state.isSaving,
                enabled = state.canSave,
            )
        }
    }
    state.duplicate?.let { duplicate ->
        GiftAlertDialog(
            title = stringResource(R.string.add_gift_duplicate_title),
            message = stringResource(R.string.add_gift_duplicate_message, duplicate.title),
            confirmLabel = stringResource(R.string.add_gift_duplicate_confirm),
            onConfirm = actions.onConfirmDuplicate,
            onDismiss = actions.onDismissDuplicate,
            dismissLabel = stringResource(R.string.cancel),
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}
