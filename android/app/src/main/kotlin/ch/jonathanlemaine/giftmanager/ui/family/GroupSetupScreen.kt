package ch.jonathanlemaine.giftmanager.ui.family

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.ui.common.BodyText
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.SecondaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

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

/** Bouton à l'origine de l'action en cours : seul lui affiche l'indicateur (le ViewModel n'a qu'un `isBusy`). */
private enum class HouseholdAction { Share, Create, Join }

/** Saisie d'un code d'invitation : majuscules, sans correction ni suggestions. */
private fun codeKeyboard(imeAction: ImeAction) = KeyboardOptions(
    capitalization = KeyboardCapitalization.Characters,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Ascii,
    imeAction = imeAction,
)

/**
 * Créer ou rejoindre une famille, puis créer, rejoindre ou partager son foyer (iOS : `GroupSetupView`).
 *
 * Quatre sous-écrans ([GroupSetupMode]) qui se relaient en fondu, avec un léger glissement dans le sens de
 * la navigation (rien si animations réduites). « Se déconnecter » vit dans le menu « Options » en haut à
 * droite, comme la barre d'outils iOS. Le retour système et « Retour » ramènent au choix.
 */
@Composable
fun GroupSetupScreen(state: GroupSetupUiState, actions: GroupSetupActions, modifier: Modifier = Modifier) {
    val reduceMotion = GiftTheme.reduceMotion
    BackHandler(enabled = state.mode == GroupSetupMode.Create || state.mode == GroupSetupMode.Join) { actions.onBack() }

    Box(
        modifier = modifier
            .fcScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AnimatedContent(
            targetState = state.mode,
            transitionSpec = { modeTransition(initialState, targetState, reduceMotion) },
            label = "groupSetupMode",
            modifier = Modifier.fillMaxSize(),
        ) { mode ->
            // Marges système déjà posées (et consommées) par le cadre : la colonne n'en rajoute pas.
            ScreenColumn(
                spacing = GiftTheme.spacing.l,
                horizontalAlignment = if (mode == GroupSetupMode.Choose) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                // Place réservée au bouton « Options » superposé en haut à droite.
                Box(Modifier.size(HitTarget.minimum - GiftTheme.spacing.xl))
                when (mode) {
                    GroupSetupMode.Choose -> ChooseContent(actions)
                    GroupSetupMode.Create -> CreateContent(state, actions)
                    GroupSetupMode.Join -> JoinContent(state, actions)
                    GroupSetupMode.Household -> HouseholdContent(state, actions)
                }
            }
        }
        OptionsMenu(onSignOut = actions.onSignOut, modifier = Modifier.align(Alignment.TopEnd).padding(end = GiftTheme.spacing.xs))
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

/** Avancer (vers un formulaire, vers le foyer) glisse depuis la droite ; revenir au choix, depuis la gauche. */
private fun modeTransition(from: GroupSetupMode, to: GroupSetupMode, reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val forward = to.ordinal > from.ordinal
    val offset = { width: Int -> (if (forward) width else -width) / 8 }
    return (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(260), offset)) togetherWith fadeOut(tween(120))
}

/** Menu « Options » (iOS : `ellipsis.circle`) : « Se déconnecter ». Cible 48 dp. */
@Composable
private fun OptionsMenu(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.options)
    Box(modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(HitTarget.minimum)
                .fcClickable(onClick = { expanded = true }, shape = CircleShape, pressedScale = 0.9f)
                .semantics { contentDescription = label },
        ) {
            Icon(GiftIcons.More, contentDescription = null, tint = colors.primary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = GiftTheme.radius.fieldShape,
            containerColor = colors.surface,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sign_out), style = GiftTheme.type.callout, color = colors.takenFg) },
                leadingIcon = { Icon(GiftIcons.SignOut, contentDescription = null, tint = colors.takenFg) },
                onClick = {
                    expanded = false
                    onSignOut()
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.ChooseContent(actions: GroupSetupActions) {
    val spacing = GiftTheme.spacing
    Image(
        painter = painterResource(Illustration.MASCOT_FAMILY.res),
        contentDescription = null,
        modifier = Modifier.size(200.dp),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        ScreenTitle(stringResource(R.string.group_setup_title), textAlign = TextAlign.Center)
        BodyText(stringResource(R.string.group_setup_message), textAlign = TextAlign.Center)
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m), modifier = Modifier.padding(top = spacing.s)) {
        PrimaryButton(stringResource(R.string.group_setup_create), actions.onShowCreate, leadingIcon = GiftIcons.Add)
        SecondaryButton(stringResource(R.string.group_setup_have_code), actions.onShowJoin, leadingIcon = GiftIcons.Mail)
    }
}

@Composable
private fun ColumnScope.CreateContent(state: GroupSetupUiState, actions: GroupSetupActions) {
    val focusManager = LocalFocusManager.current
    ScreenTitle(stringResource(R.string.group_create_title))
    FCTextField(
        label = stringResource(R.string.group_create_name),
        value = state.groupName,
        onValueChange = actions.onGroupNameChange,
        leadingIcon = GiftIcons.Group,
        placeholder = stringResource(R.string.group_create_name_prompt),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
    )
    FCTextField(
        label = stringResource(R.string.group_create_household_name),
        value = state.householdName,
        onValueChange = actions.onHouseholdNameChange,
        leadingIcon = GiftIcons.Home,
        placeholder = stringResource(R.string.group_create_household_prompt),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
    FCNotice(GiftIcons.Sparkles, stringResource(R.string.group_create_notice))
    PrimaryButton(
        text = stringResource(R.string.group_create_submit),
        onClick = actions.onCreateGroup,
        leadingIcon = GiftIcons.Checkmark,
        isLoading = state.isBusy,
        enabled = state.canCreateGroup,
    )
    TextLinkButton(stringResource(R.string.back), actions.onBack, modifier = Modifier.align(Alignment.CenterHorizontally))
}

@Composable
private fun ColumnScope.JoinContent(state: GroupSetupUiState, actions: GroupSetupActions) {
    ScreenTitle(stringResource(R.string.group_join_title))
    BodyText(stringResource(R.string.group_join_message))
    FCTextField(
        label = stringResource(R.string.group_join_code),
        value = state.groupCode,
        onValueChange = actions.onGroupCodeChange,
        leadingIcon = GiftIcons.Code,
        placeholder = stringResource(R.string.group_join_code_prompt),
        keyboardOptions = codeKeyboard(ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { if (state.canJoinGroup) actions.onJoinGroup() }),
    )
    PrimaryButton(
        text = stringResource(R.string.group_join_submit),
        onClick = actions.onJoinGroup,
        trailingIcon = GiftIcons.ArrowForward,
        isLoading = state.isBusy,
        enabled = state.canJoinGroup,
    )
    TextLinkButton(stringResource(R.string.back), actions.onBack, modifier = Modifier.align(Alignment.CenterHorizontally))
}

/** Après avoir rejoint une famille : partager son foyer existant, en créer un, ou rejoindre celui du conjoint. */
@Composable
private fun HouseholdContent(state: GroupSetupUiState, actions: GroupSetupActions) {
    val spacing = GiftTheme.spacing
    val focusManager = LocalFocusManager.current
    var lastAction by rememberSaveable { mutableStateOf<HouseholdAction?>(null) }
    fun loading(action: HouseholdAction) = state.isBusy && lastAction == action

    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Text(
            text = stringResource(R.string.household_welcome, state.currentGroupName),
            style = GiftTheme.type.title,
            color = GiftTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        BodyText(stringResource(R.string.household_intro))
    }

    // #60 : le foyer suit son parent d'une famille à l'autre — rien à ressaisir.
    state.shareableHouseholdName?.let { name ->
        HouseholdCard(title = stringResource(R.string.household_share_title), caption = stringResource(R.string.household_share_message)) {
            PrimaryButton(
                text = stringResource(R.string.household_share_action, name),
                onClick = {
                    lastAction = HouseholdAction.Share
                    actions.onShareHousehold()
                },
                leadingIcon = GiftIcons.Home,
                isLoading = loading(HouseholdAction.Share),
                enabled = !state.isBusy,
            )
        }
    }

    HouseholdCard(title = stringResource(R.string.household_create_title)) {
        FCTextField(
            label = stringResource(R.string.household_name),
            value = state.householdName,
            onValueChange = actions.onHouseholdNameChange,
            leadingIcon = GiftIcons.Home,
            placeholder = stringResource(R.string.household_name_prompt),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        PrimaryButton(
            text = stringResource(R.string.household_create_title),
            onClick = {
                lastAction = HouseholdAction.Create
                actions.onCreateHousehold()
            },
            leadingIcon = GiftIcons.Add,
            isLoading = loading(HouseholdAction.Create),
            enabled = state.canCreateHousehold,
        )
    }

    HouseholdCard(title = stringResource(R.string.household_join_title), caption = stringResource(R.string.household_join_message)) {
        FCTextField(
            label = stringResource(R.string.household_join_code),
            value = state.householdCode,
            onValueChange = actions.onHouseholdCodeChange,
            leadingIcon = GiftIcons.Key,
            placeholder = stringResource(R.string.household_join_code_prompt),
            keyboardOptions = codeKeyboard(ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (state.canJoinHousehold) {
                        lastAction = HouseholdAction.Join
                        actions.onJoinHousehold()
                    }
                },
            ),
        )
        SecondaryButton(
            text = stringResource(R.string.household_join_action),
            onClick = {
                lastAction = HouseholdAction.Join
                actions.onJoinHousehold()
            },
            leadingIcon = GiftIcons.Group,
            isLoading = loading(HouseholdAction.Join),
            enabled = state.canJoinHousehold,
        )
    }

    FCNotice(GiftIcons.Info, stringResource(R.string.household_notice))
}

/** Carte d'une option du foyer : titre (titre TalkBack), précision facultative, puis champs et bouton. */
@Composable
private fun HouseholdCard(title: String, caption: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = GiftTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
        modifier = Modifier.fillMaxWidth().fcCard(),
    ) {
        Text(text = title, style = GiftTheme.type.headline, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        caption?.let { Text(text = it, style = GiftTheme.type.caption, color = colors.textSecondary) }
        content()
    }
}

// region Aperçus

private val previewActions = GroupSetupActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@GiftPreviews
@Composable
private fun GroupSetupChoosePreview() = ScreenPreviewFrame {
    GroupSetupScreen(GroupSetupUiState(), previewActions)
}

@GiftPreviews
@Composable
private fun GroupSetupCreatePreview() = ScreenPreviewFrame {
    GroupSetupScreen(
        GroupSetupUiState(mode = GroupSetupMode.Create, groupName = "Famille Lemaine", householdName = "Foyer de Jonathan"),
        previewActions,
    )
}

@GiftPreviews
@Composable
private fun GroupSetupJoinPreview() = ScreenPreviewFrame {
    GroupSetupScreen(GroupSetupUiState(mode = GroupSetupMode.Join, groupCode = "K7Q2", error = GiftError.InvalidInvite), previewActions)
}

@GiftPreviews
@Composable
private fun GroupSetupHouseholdPreview() = ScreenPreviewFrame(height = 1100.dp) {
    GroupSetupScreen(
        GroupSetupUiState(
            mode = GroupSetupMode.Household,
            currentGroupName = "Famille Martin",
            shareableHouseholdName = "Foyer de Jonathan",
            householdName = "Foyer de Jonathan",
            householdCode = "AB12",
        ),
        previewActions,
    )
}

// endregion
