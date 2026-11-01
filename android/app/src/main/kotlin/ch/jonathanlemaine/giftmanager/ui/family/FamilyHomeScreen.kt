package ch.jonathanlemaine.giftmanager.ui.family

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Formatting
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.core.model.Household
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenContentMaxWidth
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.common.spelledOut
import ch.jonathanlemaine.giftmanager.ui.components.AvatarStack
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.EventRow
import ch.jonathanlemaine.giftmanager.ui.components.CountryFlag
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCPillLabel
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.SecondaryButton
import ch.jonathanlemaine.giftmanager.ui.components.SectionHeader
import ch.jonathanlemaine.giftmanager.ui.components.SegmentedTabs
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.fcPressable
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import java.util.UUID

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
    /** Tirer pour actualiser (iOS : `.refreshable`). */
    val onRefresh: () -> Unit = {},
    val onOpenEvent: (UUID) -> Unit = {},
    val onNewEvent: () -> Unit = {},
    val onEditEvent: (UUID) -> Unit = {},
    /** Ouvre la liste d'un enfant (dans le contexte de l'événement courant). */
    val onOpenChild: (UUID) -> Unit = {},
    val onEditChild: (UUID) -> Unit = {},
    val onAddChild: () -> Unit = {},
    val onAddAdult: () -> Unit = {},
)

private const val TAB_EVENTS = 0
private const val TAB_MEMBERS = 1

/**
 * « Notre famille » (écran 2 de la maquette, iOS : `FamilyHomeView`) : grand titre (nom de la famille),
 * avatars empilés + « N membres · M foyers », onglets Événements / Membres / Paramètres. Tirer vers le bas
 * recharge la famille.
 *
 * Câblage minimal des événements et des enfants (#72), à remplacer par l'écran de l'`ui-designer`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyHomeScreen(state: FamilyHomeUiState, actions: FamilyHomeActions, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefresh,
        state = refreshState,
        contentAlignment = Alignment.TopCenter,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = state.isRefreshing,
                containerColor = colors.surface,
                color = colors.primary,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = modifier
            .fcScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.l),
                modifier = Modifier
                    .widthIn(max = ScreenContentMaxWidth)
                    .fillMaxWidth()
                    .padding(start = spacing.xl, end = spacing.xl, top = spacing.l, bottom = spacing.xxl),
            ) {
                Header(state)
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
                    TAB_EVENTS -> EventsTab(state, actions)
                    TAB_MEMBERS -> MembersTab(state.households, actions)
                    else -> SettingsTab(state, actions)
                }
            }
        }
    }

    if (state.showJoinDialog) JoinOtherGroupDialog(state, actions)
    GiftErrorDialog(state.error, actions.onDismissError)
}

@Composable
private fun Header(state: FamilyHomeUiState) {
    val spacing = GiftTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        ScreenTitle(state.group?.name ?: stringResource(R.string.family_title_fallback))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            if (state.memberNames.isNotEmpty()) AvatarStack(names = state.memberNames, size = 40.dp)
            Text(
                text = pluralStringResource(R.plurals.family_members_count, state.memberCount, state.memberCount) + " · " +
                    pluralStringResource(R.plurals.family_households_count, state.householdCount, state.householdCount),
                style = GiftTheme.type.caption,
                color = GiftTheme.colors.textSecondary,
            )
        }
    }
}

// region Événements

/** Événements à venir puis passés ; « + » ouvre l'éditeur, une ligne ouvre la fiche (câblage provisoire). */
@Composable
private fun EventsTab(state: FamilyHomeUiState, actions: FamilyHomeActions) {
    SectionHeader(
        title = stringResource(R.string.family_events_upcoming),
        actionIcon = GiftIcons.Add,
        onAction = actions.onNewEvent,
        actionLabel = stringResource(R.string.family_events_add),
    )
    if (state.upcomingEvents.isEmpty()) {
        EmptyStateView(
            illustration = Illustration.MASCOT_SLEEPING,
            title = stringResource(R.string.family_events_empty_title),
            message = stringResource(R.string.family_events_empty_message),
            actionLabel = stringResource(R.string.family_events_add),
            onAction = actions.onNewEvent,
        )
    }
    state.upcomingEvents.forEach { EventItemRow(it, actions) }
    if (state.pastEvents.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.family_events_past))
        state.pastEvents.forEach { EventItemRow(it, actions) }
    }
}

@Composable
private fun EventItemRow(item: EventItem, actions: FamilyHomeActions) {
    val event = item.event
    // « Dans 12 jours · 8 ans » ; rien de relatif pour un événement passé.
    val subtitle = listOfNotNull(
        if (item.isPast) null else Formatting.countdownText(item.daysRemaining),
        Formatting.ageText(item.age),
    ).joinToString(" · ").ifEmpty { null }
    EventRow(
        title = event.title,
        dateText = Formatting.dateText(event.eventDate),
        subtitle = subtitle,
        kind = event.kind,
        avatarNames = item.avatarNames,
        onClick = { actions.onOpenEvent(event.id) },
    )
    if (item.canEdit) TextLinkButton(text = stringResource(R.string.family_event_edit), onClick = { actions.onEditEvent(event.id) })
}

// endregion

// region Membres

@Composable
private fun MembersTab(households: List<HouseholdItem>, actions: FamilyHomeActions) {
    if (households.isEmpty()) {
        EmptyStateView(
            illustration = Illustration.MASCOT_FAMILY,
            title = stringResource(R.string.family_members_empty_title),
            message = stringResource(R.string.family_members_empty_message),
        )
        return
    }
    households.forEach { item -> HouseholdCard(item, actions) }
}

/** Carte d'un foyer : drapeau, nom (titre TalkBack), badge « Mon foyer », parents ; code à partager si c'est le mien. */
@Composable
private fun HouseholdCard(item: HouseholdItem, actions: FamilyHomeActions) {
    val onShareText = actions.onShareText
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val household = item.household
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m), modifier = Modifier.fillMaxWidth().fcCard()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            CountryFlag(code = household.country, style = type.headline)
            Text(
                text = household.name,
                style = type.headline,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (item.isMine) MyHouseholdBadge()
        }
        if (item.parentNames.isNotEmpty()) {
            Text(text = item.parentNames.joinToString(" & "), style = type.caption, color = colors.textSecondary)
        }
        item.children.forEach { child -> ChildLine(child, canEdit = item.isMine, actions = actions) }
        if (item.isMine) {
            TextLinkButton(text = stringResource(R.string.family_add_child), onClick = actions.onAddChild)
            TextLinkButton(text = stringResource(R.string.family_add_adult), onClick = actions.onAddAdult)
        }
        val code = household.inviteCode
        if (item.isMine && code != null) {
            HorizontalDivider(thickness = 1.dp, color = colors.separator)
            HouseholdCodeRow(household, code, onShareText)
        }
    }
}

/** Un enfant du foyer : la ligne ouvre sa liste, le crayon (mon foyer seulement) son éditeur (câblage provisoire). */
@Composable
private fun ChildLine(child: Child, canEdit: Boolean, actions: FamilyHomeActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = HitTarget.minimum)
                .fcClickable(onClick = { actions.onOpenChild(child.id) }, shape = GiftTheme.radius.fieldShape, pressedScale = 0.98f),
        ) {
            ChildAvatar(name = child.firstName, emoji = child.avatarEmoji, colorName = child.avatarColor, photoUrl = child.avatarUrl)
            Text(child.firstName, style = GiftTheme.type.callout, color = GiftTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Icon(GiftIcons.ChevronRight, contentDescription = null, tint = GiftTheme.colors.textSecondary)
        }
        if (canEdit) {
            val label = stringResource(R.string.family_child_edit, child.firstName)
            Icon(
                GiftIcons.Edit,
                contentDescription = label,
                tint = GiftTheme.colors.primary,
                modifier = Modifier
                    .size(HitTarget.minimum)
                    .fcClickable(onClick = { actions.onEditChild(child.id) }, shape = CircleShape)
                    .padding(GiftTheme.spacing.m),
            )
        }
    }
}

@Composable
private fun MyHouseholdBadge() {
    Text(
        text = stringResource(R.string.family_my_household),
        style = GiftTheme.type.captionBold,
        color = GiftTheme.colors.availableFg,
        modifier = Modifier
            .background(GiftTheme.colors.availableBg, CapsuleShape)
            .padding(horizontal = GiftTheme.spacing.s, vertical = GiftTheme.spacing.xs),
    )
}

/** « Code du foyer pour mon conjoint : AB12CD34 » (code en chasse fixe, gras), toute la ligne partage le code. */
@Composable
private fun HouseholdCodeRow(household: Household, code: String, onShareText: (String) -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val shareMessage = stringResource(R.string.family_household_share_message, household.name, code)
    val description = stringResource(R.string.family_household_code, spelledOut(code))
    val prefix = stringResource(R.string.family_household_code_prefix)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HitTarget.minimum)
            .fcClickable(
                onClick = { onShareText(shareMessage) },
                onClickLabel = stringResource(R.string.family_household_share_hint),
                shape = GiftTheme.radius.fieldShape,
                pressedScale = 1f,
            )
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(GiftIcons.PersonAdd, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Text(
            text = buildAnnotatedString {
                append(prefix)
                append(' ')
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.textPrimary)) {
                    append(code)
                }
            },
            style = type.caption,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Icon(GiftIcons.Share, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
}

// endregion

// region Paramètres

@Composable
private fun SettingsTab(state: FamilyHomeUiState, actions: FamilyHomeActions) {
    val group = state.group
    if (group != null) {
        InviteCard(group, state.inviteMessage, actions.onShareText)
        state.rename?.let { RenameCard(it, isBusy = state.isBusy, actions = actions) }
    }
    if (state.groups.size > 1) MyGroupsCard(state.groups, currentId = group?.id, onSelect = actions.onSelectGroup)
    TextLinkButton(
        text = stringResource(R.string.family_join_other),
        onClick = actions.onShowJoinDialog,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Carte « Inviter la famille » : code en grand, chasse fixe, sélectionnable, lu lettre par lettre ; partage. */
@Composable
private fun InviteCard(group: FamilyGroup, inviteMessage: String?, onShareText: (String) -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val codeDescription = stringResource(R.string.family_invite_code_a11y, spelledOut(group.inviteCode))
    SettingsCard {
        CardTitle(stringResource(R.string.family_invite_title))
        Text(text = stringResource(R.string.family_invite_message), style = type.caption, color = colors.textSecondary)
        SelectionContainer {
            Text(
                text = group.inviteCode,
                style = type.largeTitle.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                color = colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background, GiftTheme.radius.fieldShape)
                    .padding(vertical = spacing.m)
                    .semantics { contentDescription = codeDescription },
            )
        }
        inviteMessage?.let { message ->
            FCPillLabel(
                text = stringResource(R.string.family_invite_share),
                icon = GiftIcons.Share,
                modifier = Modifier.fcClickable(onClick = { onShareText(message) }),
            )
        }
    }
}

/** Carte « Nom de la famille » : « Renommer » n'apparaît qu'une fois le nom réellement modifié. */
@Composable
private fun RenameCard(rename: RenameGroupState, isBusy: Boolean, actions: FamilyHomeActions) {
    val reduceMotion = GiftTheme.reduceMotion
    SettingsCard {
        FCTextField(
            label = stringResource(R.string.group_create_name),
            value = rename.name,
            onValueChange = actions.onRenameChange,
            leadingIcon = GiftIcons.Group,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (rename.canRename) actions.onRename() }),
        )
        AnimatedVisibility(
            visible = rename.canRename || (isBusy && rename.name.trim() != rename.originalName),
            enter = if (reduceMotion) fadeIn(snap()) else fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = if (reduceMotion) fadeOut(snap()) else fadeOut(tween(150)) + shrinkVertically(tween(200)),
        ) {
            SecondaryButton(
                text = stringResource(R.string.family_rename),
                onClick = actions.onRename,
                leadingIcon = GiftIcons.Edit,
                isLoading = isBusy,
            )
        }
    }
}

/** « Mes familles » : une ligne par famille, coche `secondary` sur la famille affichée (TalkBack : « sélectionné »). */
@Composable
private fun MyGroupsCard(groups: List<FamilyGroup>, currentId: UUID?, onSelect: (FamilyGroup) -> Unit) {
    val colors = GiftTheme.colors
    SettingsCard(spacing = 0.dp) {
        CardTitle(stringResource(R.string.family_my_groups), modifier = Modifier.padding(bottom = GiftTheme.spacing.s))
        Column(modifier = Modifier.selectableGroup()) {
            groups.forEach { group ->
                val selected = group.id == currentId
                val source = remember { MutableInteractionSource() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HitTarget.minimum)
                        .fcPressable(source, GiftTheme.radius.fieldShape, pressedScale = 1f)
                        .selectable(
                            selected = selected,
                            interactionSource = source,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onSelect(group) },
                        ),
                ) {
                    Text(
                        text = group.name,
                        style = if (selected) GiftTheme.type.callout.copy(fontWeight = FontWeight.SemiBold) else GiftTheme.type.callout,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(GiftIcons.Checkmark, contentDescription = null, tint = colors.secondary)
                }
            }
        }
    }
}

/** « Rejoindre une famille » : champ code (majuscules, sans correction), « Rejoindre » actif dès 6 caractères. */
@Composable
private fun JoinOtherGroupDialog(state: FamilyHomeUiState, actions: FamilyHomeActions) {
    GiftAlertDialog(
        title = stringResource(R.string.group_join_title),
        message = stringResource(R.string.group_join_message),
        confirmLabel = stringResource(R.string.group_join_submit),
        onConfirm = actions.onJoin,
        onDismiss = actions.onHideJoinDialog,
        dismissLabel = stringResource(R.string.cancel),
        confirmEnabled = state.canJoin,
    ) {
        FCTextField(
            label = stringResource(R.string.family_join_dialog_code),
            value = state.joinCode,
            onValueChange = actions.onJoinCodeChange,
            leadingIcon = GiftIcons.Code,
            placeholder = stringResource(R.string.group_join_code_prompt),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { if (state.canJoin) actions.onJoin() }),
        )
    }
}

@Composable
private fun SettingsCard(spacing: Dp = GiftTheme.spacing.m, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.fillMaxWidth().fcCard(), content = content)
}

@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = GiftTheme.type.headline,
        color = GiftTheme.colors.textPrimary,
        modifier = modifier.semantics { heading() },
    )
}

// endregion

// region Aperçus

private fun previewId(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

private val previewGroup = FamilyGroup(previewId("g1"), "Famille Lemaine", "K7Q2XM")
private val previewOtherGroup = FamilyGroup(previewId("g2"), "Famille Martin", "P3D9LA")

private val previewState = FamilyHomeUiState(
    group = previewGroup,
    memberNames = listOf("Jonathan", "Marie", "Claire", "Paul", "Mamie", "Papi", "Léa"),
    memberCount = 7,
    householdCount = 3,
    households = listOf(
        HouseholdItem(Household(previewId("h1"), "Foyer de Jonathan", "CH", "AB12CD34"), isMine = true, listOf("Jonathan", "Marie")),
        HouseholdItem(Household(previewId("h2"), "Claire & Paul", "FR"), isMine = false, listOf("Claire", "Paul")),
        HouseholdItem(Household(previewId("h3"), "Mamie et Papi", "BE"), isMine = false, listOf("Mamie", "Papi")),
    ),
    groups = listOf(previewGroup, previewOtherGroup),
    rename = RenameGroupState(previewGroup.name, "Famille Lemaine-Martin"),
    inviteMessage = "Rejoins notre famille",
)

private val previewActions = FamilyHomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@GiftPreviews
@Composable
private fun FamilyHomeEventsPreview() = ScreenPreviewFrame { FamilyHomeScreen(previewState, previewActions) }

@GiftPreviews
@Composable
private fun FamilyHomeMembersPreview() = ScreenPreviewFrame { FamilyHomeScreen(previewState.copy(tab = TAB_MEMBERS), previewActions) }

@GiftPreviews
@Composable
private fun FamilyHomeSettingsPreview() = ScreenPreviewFrame(height = 1000.dp) {
    FamilyHomeScreen(previewState.copy(tab = 2), previewActions)
}

@GiftPreviews
@Composable
private fun FamilyHomeJoinDialogPreview() = ScreenPreviewFrame {
    FamilyHomeScreen(previewState.copy(tab = 2, showJoinDialog = true, joinCode = "P3D"), previewActions)
}

// endregion
