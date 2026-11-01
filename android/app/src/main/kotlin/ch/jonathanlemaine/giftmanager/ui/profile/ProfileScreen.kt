package ch.jonathanlemaine.giftmanager.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.ui.common.BodyText
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ProfileFields
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Actions de [ProfileScreen], reliées à [ProfileViewModel]. */
data class ProfileActions(
    val onNameChange: (String) -> Unit,
    val onCountryChange: (String) -> Unit,
    val onCurrencyChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onPurchaseReminders: (Boolean) -> Unit,
    val onFamilyNews: (Boolean) -> Unit,
    val onBirthdayReminders: (Boolean) -> Unit,
    val onRequestUnshare: (FamilyGroup) -> Unit,
    val onConfirmUnshare: () -> Unit,
    val onCancelUnshare: () -> Unit,
    val onSignOut: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Profil : identité, foyer, notifications, déconnexion, suppression de compte. Câblage minimal.
 * [onOpenGallery] n'est fourni qu'en debug (galerie du design system) ; `null` → aucun lien.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
    onOpenGallery: (() -> Unit)? = null,
) {
    ScreenColumn(modifier) {
        ScreenTitle(stringResource(R.string.profile_title))
        BodyText(state.displayName)
        ProfileFields(state.name, state.country, state.currency, actions.onNameChange, actions.onCountryChange, actions.onCurrencyChange)
        if (state.hasChanges) {
            PrimaryButton(stringResource(R.string.profile_save), actions.onSave, isLoading = state.isSaving, enabled = state.canSave)
        }
        state.householdName?.let { household ->
            Text(stringResource(R.string.profile_household_visible_in, household), style = GiftTheme.type.headline, color = GiftTheme.colors.textPrimary)
            state.sharedFamilies.forEach { group ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(group.name, style = GiftTheme.type.callout, color = GiftTheme.colors.textPrimary)
                    if (state.canUnshare) TextLinkButton(stringResource(R.string.profile_unshare), { actions.onRequestUnshare(group) })
                }
            }
            BodyText(stringResource(R.string.profile_household_footer))
        }
        Text(stringResource(R.string.profile_notifications), style = GiftTheme.type.headline, color = GiftTheme.colors.textPrimary)
        ToggleRow(stringResource(R.string.profile_notif_purchases), state.purchaseReminders, actions.onPurchaseReminders)
        ToggleRow(stringResource(R.string.profile_notif_family_news), state.familyNews, actions.onFamilyNews)
        ToggleRow(stringResource(R.string.profile_notif_birthdays), state.birthdayReminders, actions.onBirthdayReminders)
        BodyText(stringResource(R.string.profile_notifications_footer))
        FCNotice(GiftIcons.EyeSlash, stringResource(R.string.profile_surprise_notice), tone = FCNoticeTone.SURPRISE)
        TextLinkButton(stringResource(R.string.sign_out), actions.onSignOut)
        if (onOpenGallery != null) TextLinkButton(stringResource(R.string.home_gallery_link), onOpenGallery)
        TextLinkButton(stringResource(R.string.profile_delete), actions.onRequestDelete)
    }
    state.unsharing?.let { group ->
        ConfirmDialog(
            title = stringResource(R.string.profile_unshare_title, group.name),
            message = stringResource(R.string.profile_unshare_message),
            confirm = stringResource(R.string.profile_unshare),
            onConfirm = actions.onConfirmUnshare,
            onDismiss = actions.onCancelUnshare,
        )
    }
    if (state.confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.profile_delete_title),
            message = stringResource(R.string.profile_delete_message),
            confirm = stringResource(R.string.profile_delete_confirm),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onCancelDelete,
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = GiftTheme.type.callout, color = GiftTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
