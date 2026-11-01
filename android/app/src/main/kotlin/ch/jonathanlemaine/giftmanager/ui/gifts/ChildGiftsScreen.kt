package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Formatting
import ch.jonathanlemaine.giftmanager.core.gifts.GiftActionReducer
import ch.jonathanlemaine.giftmanager.core.gifts.GiftTab
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenContentMaxWidth
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.common.TopBarTextAction
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.GiftCard
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.IndicativeRateNote
import ch.jonathanlemaine.giftmanager.ui.components.SegmentedTabs
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.fcPressable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import java.util.UUID

/** Rappels de la liste de cadeaux d'un enfant. */
data class ChildGiftsActions(
    val onBack: () -> Unit,
    val onSelectTab: (GiftTab) -> Unit,
    /** `null` : tous les cadeaux. */
    val onSelectEvent: (UUID?) -> Unit,
    val onOpenItem: (WishItem) -> Unit,
    val onAdd: () -> Unit,
    val onDelete: (WishItem) -> Unit,
    val onToggleOwned: (WishItem) -> Unit,
    val onToggleFavorite: (WishItem) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onRefresh: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Liste d'un enfant (écran 3 de la maquette, iOS : `ChildGiftsView`).
 *
 * En-tête (avatar, prénom, « 8 ans · Ses envies » ou le foyer), onglets Liste / Possède déjà / Idées (ce dernier
 * jamais pour les parents) avec leurs compteurs, pastille « événement affiché » (#61), puis les cartes. Le statut
 * d'une carte vient de [GiftActionReducer.badge] : **aucun badge** sur la liste d'un parent (mode surprise).
 *
 * Actions d'une carte (iOS : balayages) : menu ⋮, appui long, et actions TalkBack — « Très envie », « Il l'a
 * déjà » / « Remettre dans la liste », « Supprimer » (avec confirmation). « Ordonner » ajoute ↑ / ↓ à chaque
 * souhait (iOS : poignées de déplacement), aussi proposés à TalkBack comme actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildGiftsScreen(state: ChildGiftsUiState, actions: ChildGiftsActions, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    val listState = rememberLazyListState()
    val titleThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val titleUnderBar by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > titleThreshold }
    }
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    var reordering by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.canReorder) { if (!state.canReorder) reordering = false }
    var pendingDelete by remember { mutableStateOf<WishItem?>(null) }
    val refreshState = rememberPullToRefreshState()
    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val visible = state.visibleItems

    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.BACK,
                onNavigate = actions.onBack,
                title = state.child?.firstName,
                titleVisible = titleUnderBar,
                showDivider = scrolled,
            ) { TopBarActions(state, reordering, onToggleReorder = { reordering = !reordering }, onAdd = actions.onAdd) }
        },
    ) {
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
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.m),
                contentPadding = PaddingValues(start = spacing.xl, end = spacing.xl, top = spacing.s, bottom = spacing.xxl + bottomInset),
                modifier = Modifier.fillMaxSize(),
            ) {
                val column = Modifier.widthIn(max = ScreenContentMaxWidth).fillMaxWidth()
                item(key = "header") { Header(state, column) }
                if (state.readOnly) {
                    item(key = "archive") {
                        FCNotice(
                            icon = if (state.isParent) GiftIcons.Gift else GiftIcons.Archive,
                            text = stringResource(if (state.isParent) R.string.gifts_past_notice_parent else R.string.gifts_past_notice),
                            modifier = column,
                        )
                    }
                }
                item(key = "tabs") { Tabs(state, actions, column) }
                item(key = "filter") {
                    Box(column) { EventFilter(state, actions) }
                }
                when {
                    state.isLoading && !state.hasLoaded -> item(key = "loading") {
                        GiftLoadingView(size = 72.dp, label = stringResource(R.string.gifts_loading), modifier = column.padding(top = spacing.xxl))
                    }
                    state.hasLoaded && visible.isEmpty() -> item(key = "empty-${state.tab}") { EmptyState(state, actions, column) }
                }
                itemsIndexed(visible, key = { _, item -> item.id }) { index, item ->
                    GiftRow(
                        state = state,
                        item = item,
                        index = index,
                        count = visible.size,
                        reordering = reordering,
                        actions = actions,
                        onRequestDelete = { pendingDelete = item },
                        modifier = column.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = if (GiftTheme.reduceMotion) null else androidx.compose.animation.core.spring(stiffness = 500f),
                        ),
                    )
                }
                // Un prix converti est affiché (« ≈ 214 € ») : sa mention, une fois sous la liste (#74, cf. fiche cadeau).
                if (state.hasLoaded && visible.isNotEmpty() && state.hasConversion) {
                    item(key = "rate-note") { IndicativeRateNote(state.rateDateText, modifier = column.padding(top = spacing.s)) }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        GiftAlertDialog(
            title = stringResource(R.string.gifts_delete_title, item.title),
            message = stringResource(R.string.gifts_delete_message),
            confirmLabel = stringResource(R.string.gifts_delete),
            onConfirm = {
                pendingDelete = null
                actions.onDelete(item)
            },
            onDismiss = { pendingDelete = null },
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

/** « Archivé » (événement passé), « Ordonner » / « OK », puis ajouter (souhait, cadeau possédé) ou proposer une idée. */
@Composable
private fun TopBarActions(state: ChildGiftsUiState, reordering: Boolean, onToggleReorder: () -> Unit, onAdd: () -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    if (state.readOnly) {
        val archived = stringResource(R.string.gifts_archived)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
            modifier = Modifier
                .padding(end = GiftTheme.spacing.m)
                .clearAndSetSemantics { contentDescription = archived },
        ) {
            Icon(GiftIcons.Archive, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(type.captionBold.glyphSize(1.3f)))
            Text(archived, style = type.captionBold, color = colors.textSecondary)
        }
        return
    }
    if (state.canReorder) {
        TopBarTextAction(stringResource(if (reordering) R.string.gifts_reorder_done else R.string.gifts_reorder), onClick = onToggleReorder)
    }
    val mode = state.addMode ?: return
    val (icon, label) = when {
        mode.kind == WishKind.IDEA -> GiftIcons.Lightbulb to stringResource(R.string.gifts_add_idea)
        mode.owned -> GiftIcons.AddCircle to stringResource(R.string.gifts_add_owned_a11y)
        else -> GiftIcons.AddCircle to stringResource(R.string.gifts_add_wish_a11y)
    }
    RoundIconButton(icon = icon, label = label, onClick = onAdd)
}

/** Avatar 56 dp, prénom (titre TalkBack), « 8 ans · Ses envies » ou « 8 ans · Foyer Martin » : un seul nœud. */
@Composable
private fun Header(state: ChildGiftsUiState, modifier: Modifier) {
    val child = state.child ?: return
    val colors = GiftTheme.colors
    val subtitle = listOfNotNull(
        if (child.isAdult) null else Formatting.ageText(child.age()),
        if (state.isParent) stringResource(R.string.gifts_header_wishes) else state.householdName,
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
        modifier = modifier.padding(vertical = GiftTheme.spacing.xs).semantics(mergeDescendants = true) { heading() },
    ) {
        ChildAvatar(
            name = child.firstName,
            emoji = child.avatarEmoji,
            colorName = child.avatarColor,
            photoUrl = child.avatarUrl,
            size = 56.dp,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs)) {
            Text(child.firstName, style = GiftTheme.type.title, color = colors.textPrimary)
            if (subtitle.isNotEmpty()) Text(subtitle, style = GiftTheme.type.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun Tabs(state: ChildGiftsUiState, actions: ChildGiftsActions, modifier: Modifier) {
    val counts = state.counts
    val titles = state.tabs.map { tab ->
        when (tab) {
            GiftTab.WISHES -> stringResource(R.string.gifts_tab_wishes, counts.wishes)
            GiftTab.OWNED -> stringResource(R.string.gifts_tab_owned, counts.owned)
            GiftTab.IDEAS -> stringResource(R.string.gifts_tab_ideas, counts.ideas)
        }
    }
    SegmentedTabs(
        selectedIndex = state.tabs.indexOf(state.tab).coerceAtLeast(0),
        onSelect = { actions.onSelectTab(state.tabs[it]) },
        titles = titles,
        modifier = modifier,
    )
}

/**
 * Pastille « événement affiché » (#61 : un cadeau rangé dans un autre événement semblait disparu). Capsule
 * `surface`, calendrier + titre + ▾ en `primary` ; menu « Tous les cadeaux » puis les événements de l'enfant.
 */
@Composable
private fun EventFilter(state: ChildGiftsUiState, actions: ChildGiftsActions) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    var open by rememberSaveable { mutableStateOf(false) }
    val allTitle = stringResource(R.string.gifts_all_events)
    val title = state.selectedEvent?.title ?: allTitle
    val description = stringResource(R.string.gifts_event_filter_a11y, title)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
            modifier = Modifier
                .heightIn(min = HitTarget.minimum)
                .fcClickable(
                    onClick = { open = true },
                    role = Role.DropdownList,
                    onClickLabel = stringResource(R.string.gifts_event_filter_hint),
                    shape = CapsuleShape,
                )
                .background(colors.surface, CapsuleShape)
                .padding(horizontal = GiftTheme.spacing.m)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            val glyph = Modifier.size(type.captionBold.glyphSize(1.3f))
            Icon(GiftIcons.Calendar, contentDescription = null, tint = colors.primary, modifier = glyph)
            Text(
                text = title,
                style = type.captionBold,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(GiftIcons.DropDown, contentDescription = null, tint = colors.primary, modifier = glyph)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = GiftTheme.radius.fieldShape,
            containerColor = colors.surface,
        ) {
            val options = listOf<Pair<UUID?, String>>(null to allTitle) + state.selectableEvents.map { it.id to it.title }
            options.forEach { (id, text) ->
                val isSelected = id == state.eventId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            style = if (isSelected) type.callout.copy(fontWeight = FontWeight.SemiBold) else type.callout,
                            color = colors.textPrimary,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(GiftIcons.Checkmark, contentDescription = null, tint = colors.secondary) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        actions.onSelectEvent(id)
                    },
                    modifier = Modifier.heightIn(min = HitTarget.minimum).semantics { selected = isSelected },
                )
            }
        }
    }
}

/** État vide propre à chaque onglet (mêmes loutres et textes qu'iOS), avec l'action d'ajout quand elle existe. */
@Composable
private fun EmptyState(state: ChildGiftsUiState, actions: ChildGiftsActions, modifier: Modifier) {
    val child = state.child ?: return
    val name = child.firstName
    val onAdd = state.addMode?.let { actions.onAdd }
    when (state.tab) {
        GiftTab.WISHES -> EmptyStateView(
            illustration = Illustration.EMPTY_BOX,
            title = stringResource(R.string.gifts_empty_wishes_title),
            message = when {
                !state.isParent -> stringResource(R.string.gifts_empty_wishes_other, name)
                child.isAdult -> stringResource(R.string.gifts_empty_wishes_parent_adult, name)
                else -> stringResource(R.string.gifts_empty_wishes_parent, name)
            },
            actionLabel = onAdd?.let { stringResource(R.string.gifts_empty_wishes_action) },
            onAction = onAdd,
            modifier = modifier,
        )
        GiftTab.OWNED -> EmptyStateView(
            illustration = Illustration.MASCOT_GIFT,
            title = stringResource(R.string.gifts_empty_owned_title),
            message = stringResource(if (state.isParent) R.string.gifts_empty_owned_parent else R.string.gifts_empty_owned_other, name),
            actionLabel = onAdd?.let { stringResource(R.string.gifts_add) },
            onAction = onAdd,
            modifier = modifier,
        )
        GiftTab.IDEAS -> EmptyStateView(
            illustration = Illustration.MASCOT_THINKING,
            title = stringResource(R.string.gifts_empty_ideas_title),
            message = stringResource(R.string.gifts_empty_ideas_message),
            actionLabel = onAdd?.let { stringResource(R.string.gifts_add_idea) },
            onAction = onAdd,
            modifier = modifier,
        )
    }
}

/**
 * Une carte de la liste : ouvre la fiche ; appui long (retour haptique) ou ⋮ ouvrent le menu d'actions, que
 * TalkBack propose aussi comme actions personnalisées. En mode « Ordonner », ↑ / ↓ à droite de la carte.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GiftRow(
    state: ChildGiftsUiState,
    item: WishItem,
    index: Int,
    count: Int,
    reordering: Boolean,
    actions: ChildGiftsActions,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftTheme.colors
    val link = state.linksFor(item).firstOrNull()
    val entries = menuEntries(state, item, actions, onRequestDelete)
    val canMoveUp = state.canReorder && index > 0
    val canMoveDown = state.canReorder && index < count - 1
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }

    val moveUpLabel = stringResource(R.string.gifts_move_up)
    val moveDownLabel = stringResource(R.string.gifts_move_down)
    val a11yActions = entries.map { entry -> CustomAccessibilityAction(entry.label) { entry.onClick(); true } } +
        listOfNotNull(
            if (canMoveUp) CustomAccessibilityAction(moveUpLabel) { actions.onMove(index, index - 1); true } else null,
            if (canMoveDown) CustomAccessibilityAction(moveDownLabel) { actions.onMove(index, index + 1); true } else null,
        )
    val position = if (reordering) stringResource(R.string.gifts_position, index + 1, count) else null

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        GiftCard(
            title = item.title,
            imageUrl = item.imageUrl,
            priceText = link?.priceText,
            storeText = link?.store,
            countryCode = link?.country,
            approxPriceText = link?.let(state::approxText),
            isFavorite = item.isFavorite,
            status = GiftActionReducer.badge(item, isParentView = state.isParent),
            modifier = Modifier
                .weight(1f)
                .fcPressable(source, GiftTheme.radius.cardShape, pressedScale = 0.98f)
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.gift_open),
                    onLongClickLabel = if (entries.isEmpty()) null else stringResource(R.string.gifts_card_actions_hint),
                    onLongClick = if (entries.isEmpty()) {
                        null
                    } else {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        }
                    },
                    onClick = { actions.onOpenItem(item) },
                )
                .semantics {
                    if (a11yActions.isNotEmpty()) customActions = a11yActions
                    if (position != null) stateDescription = position
                },
            trailingAction = if (entries.isEmpty() || reordering) {
                null
            } else {
                {
                    Box {
                        RoundIconButton(
                            icon = GiftIcons.More,
                            label = stringResource(R.string.gifts_card_actions, item.title),
                            onClick = { menuOpen = true },
                            tint = colors.textSecondary,
                        )
                        GiftActionsMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, entries = entries)
                    }
                }
            },
        )
        if (reordering) {
            Column(modifier = Modifier.padding(start = GiftTheme.spacing.xs)) {
                RoundIconButton(
                    icon = GiftIcons.MoveUp,
                    label = stringResource(R.string.gifts_move_up_item, item.title),
                    onClick = { actions.onMove(index, index - 1) },
                    enabled = canMoveUp,
                )
                RoundIconButton(
                    icon = GiftIcons.MoveDown,
                    label = stringResource(R.string.gifts_move_down_item, item.title),
                    onClick = { actions.onMove(index, index + 1) },
                    enabled = canMoveDown,
                )
            }
        }
    }
}

/** Actions permises sur une carte, d'après l'état (jamais recalculées ici). */
@Composable
private fun menuEntries(state: ChildGiftsUiState, item: WishItem, actions: ChildGiftsActions, onRequestDelete: () -> Unit): List<GiftMenuEntry> {
    val colors = GiftTheme.colors
    val favorite = if (state.canToggleFavorite(item)) {
        GiftMenuEntry(
            label = stringResource(if (item.isFavorite) R.string.gifts_unfavorite else R.string.gifts_favorite),
            icon = if (item.isFavorite) GiftIcons.HeartOutline else GiftIcons.Heart,
            tint = colors.heart,
            onClick = { actions.onToggleFavorite(item) },
        )
    } else {
        null
    }
    val owned = if (state.canToggleOwned(item)) {
        GiftMenuEntry(
            label = stringResource(if (item.owned) R.string.gifts_back_to_list else R.string.gifts_already_owned),
            icon = if (item.owned) GiftIcons.Undo else GiftIcons.Verified,
            tint = colors.secondary,
            onClick = { actions.onToggleOwned(item) },
        )
    } else {
        null
    }
    val delete = if (state.canEdit(item)) {
        GiftMenuEntry(
            label = stringResource(R.string.gifts_delete),
            icon = GiftIcons.Delete,
            tint = colors.takenFg,
            destructive = true,
            onClick = onRequestDelete,
        )
    } else {
        null
    }
    return listOfNotNull(favorite, owned, delete)
}

// region Aperçus

private val previewActions = ChildGiftsActions({}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})

private fun previewState(isParent: Boolean, tab: GiftTab = GiftTab.WISHES, readOnly: Boolean = false) = ChildGiftsUiState(
    child = GiftPreviewData.leo,
    householdName = "Foyer de Jonathan",
    isParent = isParent,
    readOnly = readOnly,
    eventId = GiftPreviewData.christmas.id,
    selectableEvents = listOf(GiftPreviewData.christmas, GiftPreviewData.birthday),
    items = if (isParent) GiftPreviewData.parentItems else GiftPreviewData.familyItems,
    links = GiftPreviewData.links(if (isParent) GiftPreviewData.parentItems else GiftPreviewData.familyItems),
    preferredCountry = "CH",
    tab = tab,
    isLoading = false,
    hasLoaded = true,
)

@GiftPreviews
@Composable
private fun ChildGiftsParentPreview() = ScreenPreviewFrame(height = 900.dp) {
    ChildGiftsScreen(previewState(isParent = true), previewActions)
}

@GiftPreviews
@Composable
private fun ChildGiftsFamilyPreview() = ScreenPreviewFrame(height = 1100.dp) {
    ChildGiftsScreen(previewState(isParent = false), previewActions)
}

@GiftPreviews
@Composable
private fun ChildGiftsEmptyIdeasPreview() = ScreenPreviewFrame {
    ChildGiftsScreen(previewState(isParent = false, tab = GiftTab.IDEAS).copy(items = emptyList()), previewActions)
}

@GiftPreviews
@Composable
private fun ChildGiftsArchivedPreview() = ScreenPreviewFrame {
    ChildGiftsScreen(previewState(isParent = true, readOnly = true, tab = GiftTab.OWNED), previewActions)
}

@GiftPreviews
@Composable
private fun ChildGiftsLoadingPreview() = ScreenPreviewFrame {
    ChildGiftsScreen(previewState(isParent = true).copy(items = emptyList(), isLoading = true, hasLoaded = false), previewActions)
}

// endregion
