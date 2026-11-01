package ch.jonathanlemaine.giftmanager.ui.family

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.BodyText
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.SecondaryButton
import ch.jonathanlemaine.giftmanager.ui.components.SegmentedTabs
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Actions de [FamilyHomeScreen], reliées à [FamilyHomeViewModel]. */
data class FamilyHomeActions(
    val onSelectTab: (Int) -> Unit,
    val onRenameChange: (String) -> Unit,
    val onRename: () -> Unit,
    val onSelectGroup: (FamilyGroup) -> Unit,
    /** Partage le texte (message d'invitation ou code du foyer) via la feuille de partage système. */
    val onShareText: (String) -> Unit,
    val onShowJoinDialog: () -> Unit,
    val onHideJoinDialog: () -> Unit,
    val onJoinCodeChange: (String) -> Unit,
    val onJoin: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * « Notre famille » : en-tête, onglets Événements / Membres / Paramètres. Câblage minimal : les événements
 * (#72) et les enfants des foyers (#72) s'ajouteront ; le `ui-designer` refait la mise en page.
 */
@Composable
fun FamilyHomeScreen(state: FamilyHomeUiState, actions: FamilyHomeActions, modifier: Modifier = Modifier) {
    ScreenColumn(modifier) {
        ScreenTitle(state.group?.name ?: stringResource(R.string.family_title_fallback))
        BodyText(
            pluralStringResource(R.plurals.family_members_count, state.memberCount, state.memberCount) + " · " +
                pluralStringResource(R.plurals.family_households_count, state.householdCount, state.householdCount),
        )
        SegmentedTabs(
            selectedIndex = state.tab,
            onSelect = actions.onSelectTab,
            titles = listOf(
                stringResource(R.string.family_tab_events),
                stringResource(R.string.family_tab_members),
                stringResource(R.string.family_tab_settings),
            ),
        )
        when (state.tab) {
            0 -> BodyText(stringResource(R.string.family_events_placeholder))
            1 -> state.households.forEach { item ->
                Column {
                    Text(item.household.name, style = GiftTheme.type.headline, color = GiftTheme.colors.textPrimary)
                    if (item.isMine) BodyText(stringResource(R.string.family_my_household))
                    if (item.parentNames.isNotEmpty()) BodyText(item.parentNames.joinToString(" & "))
                    val code = item.household.inviteCode
                    if (item.isMine && code != null) {
                        val shareMessage = stringResource(R.string.family_household_share_message, item.household.name, code)
                        TextLinkButton(
                            text = stringResource(R.string.family_household_code, code),
                            onClick = { actions.onShareText(shareMessage) },
                        )
                    }
                }
            }
            else -> SettingsTab(state, actions)
        }
    }
    if (state.showJoinDialog) {
        AlertDialog(
            onDismissRequest = actions.onHideJoinDialog,
            title = { Text(stringResource(R.string.group_join_title)) },
            text = { FCTextField(stringResource(R.string.family_join_dialog_code), state.joinCode, actions.onJoinCodeChange) },
            confirmButton = { TextButton(onClick = actions.onJoin, enabled = state.canJoin) { Text(stringResource(R.string.group_join_submit)) } },
            dismissButton = { TextButton(onClick = actions.onHideJoinDialog) { Text(stringResource(R.string.cancel)) } },
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

@Composable
private fun SettingsTab(state: FamilyHomeUiState, actions: FamilyHomeActions) {
    val group = state.group ?: return
    Text(stringResource(R.string.family_invite_title), style = GiftTheme.type.headline, color = GiftTheme.colors.textPrimary)
    BodyText(stringResource(R.string.family_invite_message))
    Text(group.inviteCode, style = GiftTheme.type.title, color = GiftTheme.colors.primary)
    state.inviteMessage?.let { message ->
        SecondaryButton(stringResource(R.string.family_invite_share), { actions.onShareText(message) }, leadingIcon = GiftIcons.Share)
    }
    state.rename?.let { rename ->
        FCTextField(stringResource(R.string.group_create_name), rename.name, actions.onRenameChange)
        if (rename.canRename) SecondaryButton(stringResource(R.string.family_rename), actions.onRename, isLoading = state.isBusy)
    }
    if (state.groups.size > 1) {
        Text(stringResource(R.string.family_my_groups), style = GiftTheme.type.headline, color = GiftTheme.colors.textPrimary)
        state.groups.forEach { g ->
            Text(
                text = if (g.id == group.id) "✓ ${g.name}" else g.name,
                style = GiftTheme.type.callout,
                color = GiftTheme.colors.textPrimary,
                modifier = Modifier.clickable { actions.onSelectGroup(g) }.padding(vertical = GiftTheme.spacing.s),
            )
        }
    }
    TextLinkButton(stringResource(R.string.family_join_other), actions.onShowJoinDialog)
}
