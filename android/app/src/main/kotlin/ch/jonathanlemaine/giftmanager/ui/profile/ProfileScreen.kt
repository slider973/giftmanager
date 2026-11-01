package ch.jonathanlemaine.giftmanager.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.BuildConfig
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.push.NotificationPermission
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ProfileFields
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.common.SettingsActionRow
import ch.jonathanlemaine.giftmanager.ui.common.SettingsDivider
import ch.jonathanlemaine.giftmanager.ui.common.SettingsRow
import ch.jonathanlemaine.giftmanager.ui.common.SettingsSection
import ch.jonathanlemaine.giftmanager.ui.common.ToggleRow
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import java.util.UUID

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
 * Profil (iOS : `ProfileView`, liste groupée de type « Réglages ») : en-tête, Mon profil, foyer partagé,
 * notifications, rappel du mode surprise, déconnexion, puis suppression du compte, isolée tout en bas.
 *
 * [onOpenGallery] n'est fourni qu'en debug (galerie du design system) ; `null` → aucun lien.
 * [notifications] : autorisation du système (#76), pour le bandeau « notifications désactivées ».
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
    onOpenGallery: (() -> Unit)? = null,
    notifications: NotificationAccess = NotificationAccess(),
) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    ScreenColumn(modifier = modifier, spacing = spacing.xl) {
        ScreenTitle(stringResource(R.string.profile_title))
        Header(state)
        IdentitySection(state, actions)
        HouseholdSection(state, actions)
        NotificationsSection(state, actions, notifications)
        FCNotice(GiftIcons.EyeSlash, stringResource(R.string.profile_surprise_notice), tone = FCNoticeTone.SURPRISE)
        SettingsSection(title = null) {
            SettingsActionRow(stringResource(R.string.sign_out), actions.onSignOut, icon = GiftIcons.SignOut)
            if (onOpenGallery != null) {
                SettingsDivider()
                SettingsActionRow(stringResource(R.string.home_gallery_link), onOpenGallery, icon = GiftIcons.Sparkles, color = colors.textPrimary)
            }
        }
        // `takenFg` plutôt que le rouge Material : 5,1:1 sur `surface` en clair, AA comme sur iOS.
        SettingsSection(title = null) {
            SettingsActionRow(stringResource(R.string.profile_delete), actions.onRequestDelete, icon = GiftIcons.Delete, color = colors.takenFg)
        }
        Text(
            text = stringResource(R.string.profile_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = GiftTheme.type.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.s),
        )
    }

    state.unsharing?.let { group ->
        GiftAlertDialog(
            title = stringResource(R.string.profile_unshare_title, group.name),
            message = stringResource(R.string.profile_unshare_message),
            confirmLabel = stringResource(R.string.profile_unshare),
            onConfirm = actions.onConfirmUnshare,
            onDismiss = actions.onCancelUnshare,
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    if (state.confirmingDelete) {
        GiftAlertDialog(
            title = stringResource(R.string.profile_delete_title),
            message = stringResource(R.string.profile_delete_message),
            confirmLabel = stringResource(R.string.profile_delete_confirm),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onCancelDelete,
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

/** Avatar 64 dp (initiale du prénom saisi), prénom enregistré, foyer. Un seul nœud TalkBack. */
@Composable
private fun Header(state: ProfileUiState) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val description = listOfNotNull(state.displayName, state.householdName).joinToString(", ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.l),
        // L'avatar est décoratif (iOS : masqué à VoiceOver) : seul « Jonathan, Foyer de Jonathan » est lu.
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
    ) {
        ChildAvatar(name = state.name.ifBlank { "?" }, emoji = null, colorName = null, size = 64.dp)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.weight(1f)) {
            Text(text = state.displayName, style = type.title, color = colors.textPrimary)
            state.householdName?.let { household ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Icon(GiftIcons.Home, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Text(text = household, style = type.caption, color = colors.textSecondary)
                }
            }
        }
    }
}

/** « Mon profil » : prénom, pays, devise ; « Enregistrer » apparaît seulement s'il y a des changements. */
@Composable
private fun IdentitySection(state: ProfileUiState, actions: ProfileActions) {
    val spacing = GiftTheme.spacing
    val reduceMotion = GiftTheme.reduceMotion
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        SectionTitle(stringResource(R.string.profile_section_me))
        ProfileFields(
            state.name,
            state.country,
            state.currency,
            actions.onNameChange,
            actions.onCountryChange,
            actions.onCurrencyChange,
        )
        SectionFooter(stringResource(R.string.profile_setup_message))
        AnimatedVisibility(
            visible = state.hasChanges,
            enter = if (reduceMotion) fadeIn(snap()) else fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = if (reduceMotion) fadeOut(snap()) else fadeOut(tween(150)) + shrinkVertically(tween(200)),
        ) {
            PrimaryButton(
                text = stringResource(R.string.profile_save),
                onClick = actions.onSave,
                leadingIcon = GiftIcons.Checkmark,
                isLoading = state.isSaving,
                enabled = state.canSave || state.isSaving,
                modifier = Modifier.padding(top = spacing.s),
            )
        }
    }
}

/** #60 — « « Foyer » est visible dans » : une ligne par famille, « Retirer » s'il y en a plusieurs. */
@Composable
private fun HouseholdSection(state: ProfileUiState, actions: ProfileActions) {
    val household = state.householdName ?: return
    val colors = GiftTheme.colors
    SettingsSection(
        title = stringResource(R.string.profile_household_visible_in, household),
        footer = stringResource(R.string.profile_household_footer),
    ) {
        state.sharedFamilies.forEachIndexed { index, group ->
            if (index > 0) SettingsDivider()
            SettingsRow(icon = GiftIcons.Group) {
                Text(text = group.name, style = GiftTheme.type.callout, color = colors.textPrimary, modifier = Modifier.weight(1f))
                if (state.canUnshare) {
                    UnshareButton(onClick = { actions.onRequestUnshare(group) })
                }
            }
        }
    }
}

@Composable
private fun UnshareButton(onClick: () -> Unit) {
    // Lien texte `takenFg` (destructif) : même cible de 48 dp que TextLinkButton, colorée à la main.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fcClickable(onClick = onClick, shape = GiftTheme.radius.fieldShape, pressedScale = 1f)
            .heightIn(min = HitTarget.minimum)
            .padding(horizontal = GiftTheme.spacing.s),
    ) {
        Text(
            text = stringResource(R.string.profile_unshare),
            style = GiftTheme.type.callout.copy(fontWeight = FontWeight.Medium),
            color = GiftTheme.colors.takenFg,
        )
    }
}

/**
 * Autorisation des notifications vue du Profil (#76) : [permission] et les deux issues possibles —
 * [onRequest] (boîte du système, tant qu'elle peut encore s'afficher) et [onOpenSettings] (réglages d'Android).
 */
data class NotificationAccess(
    val permission: NotificationPermission = NotificationPermission.Granted,
    val onRequest: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/**
 * Notifications : trois interrupteurs, libellés d'iOS ; la note rappelle que rien ne trahit la surprise.
 * Si l'autorisation manque alors qu'un interrupteur est actif, un bandeau `WARNING` le dit sous le titre :
 * « Activer » tant que le système peut encore demander, « Ouvrir les réglages » une fois la demande refusée.
 */
@Composable
private fun NotificationsSection(state: ProfileUiState, actions: ProfileActions, access: NotificationAccess) {
    val anyOn = state.purchaseReminders || state.familyNews || state.birthdayReminders
    val blocked = access.permission == NotificationPermission.CanAsk || access.permission == NotificationPermission.PermanentlyDenied
    SettingsSection(
        title = stringResource(R.string.profile_notifications),
        footer = stringResource(R.string.profile_notifications_footer),
        notice = if (anyOn && blocked) {
            { NotificationsDisabledNotice(access) }
        } else {
            null
        },
    ) {
        ToggleRow(GiftIcons.Calendar, stringResource(R.string.profile_notif_purchases), state.purchaseReminders, actions.onPurchaseReminders)
        SettingsDivider()
        ToggleRow(GiftIcons.Sparkles, stringResource(R.string.profile_notif_family_news), state.familyNews, actions.onFamilyNews)
        SettingsDivider()
        ToggleRow(GiftIcons.Cake, stringResource(R.string.profile_notif_birthdays), state.birthdayReminders, actions.onBirthdayReminders)
    }
}

@Composable
private fun NotificationsDisabledNotice(access: NotificationAccess) {
    val canAsk = access.permission == NotificationPermission.CanAsk
    FCNotice(
        icon = GiftIcons.BellOff,
        text = stringResource(R.string.profile_notif_disabled),
        tone = FCNoticeTone.WARNING,
        actionLabel = stringResource(if (canAsk) R.string.profile_notif_enable else R.string.profile_notif_open_settings),
        onAction = if (canAsk) access.onRequest else access.onOpenSettings,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = GiftTheme.type.captionBold,
        color = GiftTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = GiftTheme.spacing.l).semantics { heading() },
    )
}

@Composable
private fun SectionFooter(text: String) {
    Text(
        text = text,
        style = GiftTheme.type.caption,
        color = GiftTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = GiftTheme.spacing.l),
    )
}

// region Aperçus

private fun previewGroup(seed: String, name: String) = FamilyGroup(UUID.nameUUIDFromBytes(seed.toByteArray()), name, "K7Q2XM")

private val previewState = ProfileUiState(
    displayName = "Jonathan",
    name = "Jonathan",
    country = "CH",
    currency = "CHF",
    householdName = "Foyer de Jonathan",
    sharedFamilies = listOf(previewGroup("g1", "Famille Lemaine"), previewGroup("g2", "Famille Martin")),
    familyNews = false,
)

private val previewActions = ProfileActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@GiftPreviews
@Composable
private fun ProfilePreview() = ScreenPreviewFrame(height = 1500.dp) {
    ProfileScreen(previewState, previewActions, onOpenGallery = {})
}

@GiftPreviews
@Composable
private fun ProfileNotificationsDeniedPreview() = ScreenPreviewFrame(height = 1600.dp) {
    ProfileScreen(previewState, previewActions, notifications = NotificationAccess(NotificationPermission.PermanentlyDenied))
}

@GiftPreviews
@Composable
private fun ProfileNotificationsCanAskPreview() = PreviewFrame {
    NotificationsSection(previewState, previewActions, NotificationAccess(NotificationPermission.CanAsk))
}

@GiftPreviews
@Composable
private fun ProfileEditingPreview() = ScreenPreviewFrame {
    ProfileScreen(previewState.copy(name = "Jonathan L.", hasChanges = true, sharedFamilies = previewState.sharedFamilies.take(1)), previewActions)
}

@GiftPreviews
@Composable
private fun ProfileDeleteDialogPreview() = ScreenPreviewFrame {
    ProfileScreen(previewState.copy(confirmingDelete = true), previewActions)
}

@GiftPreviews
@Composable
private fun ProfileErrorPreview() = ScreenPreviewFrame {
    ProfileScreen(previewState.copy(error = GiftError.Network), previewActions)
}

// endregion
