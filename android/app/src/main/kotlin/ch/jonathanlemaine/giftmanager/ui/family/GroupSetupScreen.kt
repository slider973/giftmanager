package ch.jonathanlemaine.giftmanager.ui.family

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.BodyText
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.SecondaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton

/** Actions de [GroupSetupScreen], reliées à [GroupSetupViewModel]. */
data class GroupSetupActions(
    val onShowCreate: () -> Unit,
    val onShowJoin: () -> Unit,
    val onBack: () -> Unit,
    val onGroupNameChange: (String) -> Unit,
    val onHouseholdNameChange: (String) -> Unit,
    val onGroupCodeChange: (String) -> Unit,
    val onHouseholdCodeChange: (String) -> Unit,
    val onCreateGroup: () -> Unit,
    val onJoinGroup: () -> Unit,
    val onCreateHousehold: () -> Unit,
    val onJoinHousehold: () -> Unit,
    val onShareHousehold: () -> Unit,
    val onSignOut: () -> Unit,
    val onDismissError: () -> Unit,
)

/** Créer ou rejoindre une famille, puis son foyer. Câblage minimal : le `ui-designer` le remplace. */
@Composable
fun GroupSetupScreen(state: GroupSetupUiState, actions: GroupSetupActions, modifier: Modifier = Modifier) {
    ScreenColumn(modifier) {
        when (state.mode) {
            GroupSetupMode.Choose -> {
                ScreenTitle(stringResource(R.string.group_setup_title))
                BodyText(stringResource(R.string.group_setup_message))
                PrimaryButton(stringResource(R.string.group_setup_create), actions.onShowCreate)
                SecondaryButton(stringResource(R.string.group_setup_have_code), actions.onShowJoin)
                TextLinkButton(stringResource(R.string.sign_out), actions.onSignOut)
            }
            GroupSetupMode.Create -> {
                ScreenTitle(stringResource(R.string.group_create_title))
                FCTextField(
                    stringResource(R.string.group_create_name), state.groupName, actions.onGroupNameChange,
                    placeholder = stringResource(R.string.group_create_name_prompt),
                )
                FCTextField(
                    stringResource(R.string.group_create_household_name), state.householdName, actions.onHouseholdNameChange,
                    placeholder = stringResource(R.string.group_create_household_prompt),
                )
                BodyText(stringResource(R.string.group_create_notice))
                PrimaryButton(stringResource(R.string.group_create_submit), actions.onCreateGroup, isLoading = state.isBusy, enabled = state.canCreateGroup)
                TextLinkButton(stringResource(R.string.back), actions.onBack)
            }
            GroupSetupMode.Join -> {
                ScreenTitle(stringResource(R.string.group_join_title))
                BodyText(stringResource(R.string.group_join_message))
                FCTextField(stringResource(R.string.group_join_code), state.groupCode, actions.onGroupCodeChange, placeholder = "ABC123")
                PrimaryButton(stringResource(R.string.group_join_submit), actions.onJoinGroup, isLoading = state.isBusy, enabled = state.canJoinGroup)
                TextLinkButton(stringResource(R.string.back), actions.onBack)
            }
            GroupSetupMode.Household -> {
                ScreenTitle(stringResource(R.string.household_welcome, state.currentGroupName))
                BodyText(stringResource(R.string.household_intro))
                state.shareableHouseholdName?.let { name ->
                    BodyText(stringResource(R.string.household_share_message))
                    PrimaryButton(stringResource(R.string.household_share_action, name), actions.onShareHousehold, isLoading = state.isBusy)
                }
                FCTextField(
                    stringResource(R.string.household_name), state.householdName, actions.onHouseholdNameChange,
                    placeholder = stringResource(R.string.household_name_prompt),
                )
                PrimaryButton(stringResource(R.string.household_create_title), actions.onCreateHousehold, isLoading = state.isBusy, enabled = state.canCreateHousehold)
                BodyText(stringResource(R.string.household_join_message))
                FCTextField(
                    stringResource(R.string.household_join_code), state.householdCode, actions.onHouseholdCodeChange,
                    placeholder = stringResource(R.string.household_join_code_prompt),
                )
                SecondaryButton(stringResource(R.string.household_join_action), actions.onJoinHousehold, isLoading = state.isBusy, enabled = state.canJoinHousehold)
                BodyText(stringResource(R.string.household_notice))
                TextLinkButton(stringResource(R.string.sign_out), actions.onSignOut)
            }
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}
