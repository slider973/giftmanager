package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.gifts.GiftActionReducer
import ch.jonathanlemaine.giftmanager.core.gifts.GiftTab
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.common.TopBarTextAction
import ch.jonathanlemaine.giftmanager.ui.components.GiftCard
import ch.jonathanlemaine.giftmanager.ui.components.SegmentedTabs
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import java.util.UUID

/** Rappels de la liste de cadeaux d'un enfant. L'écran définitif (ui-designer) garde cette surface. */
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
 * Câblage minimal de la liste d'un enfant : onglets, filtre d'événement, cartes et actions de ligne. Le statut d'une
 * carte vient de [GiftActionReducer.badge] : jamais de badge sur la liste d'un parent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildGiftsScreen(state: ChildGiftsUiState, actions: ChildGiftsActions, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    val counts = state.counts
    val tabTitles = state.tabs.map { tab ->
        when (tab) {
            GiftTab.WISHES -> stringResource(R.string.gifts_tab_wishes, counts.wishes)
            GiftTab.OWNED -> stringResource(R.string.gifts_tab_owned, counts.owned)
            GiftTab.IDEAS -> stringResource(R.string.gifts_tab_ideas, counts.ideas)
        }
    }
    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.BACK,
                onNavigate = actions.onBack,
                title = state.child?.firstName,
                actions = {
                    if (state.addMode != null) {
                        TopBarTextAction(stringResource(R.string.gifts_add), actions.onAdd)
                    }
                },
            )
        },
    ) {
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.m),
                modifier = Modifier.fillMaxSize().padding(horizontal = spacing.xl),
            ) {
                item {
                    SegmentedTabs(
                        selectedIndex = state.tabs.indexOf(state.tab).coerceAtLeast(0),
                        onSelect = { actions.onSelectTab(state.tabs[it]) },
                        titles = tabTitles,
                    )
                }
                item { EventFilter(state, actions) }
                if (state.hasLoaded && state.visibleItems.isEmpty()) {
                    item { Text(stringResource(R.string.gifts_empty), color = GiftTheme.colors.textSecondary) }
                }
                items(state.visibleItems, key = { it.id }) { item -> GiftRow(state, item, actions) }
            }
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

@Composable
private fun EventFilter(state: ChildGiftsUiState, actions: ChildGiftsActions) {
    var open by remember { mutableStateOf(false) }
    Column {
        TextLinkButton(text = state.selectedEvent?.title ?: stringResource(R.string.gifts_all_events), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.gifts_all_events)) },
                onClick = { open = false; actions.onSelectEvent(null) },
            )
            state.selectableEvents.forEach { event ->
                DropdownMenuItem(text = { Text(event.title) }, onClick = { open = false; actions.onSelectEvent(event.id) })
            }
        }
    }
}

@Composable
private fun GiftRow(state: ChildGiftsUiState, item: WishItem, actions: ChildGiftsActions) {
    val link = state.linksFor(item).firstOrNull()
    val position = state.visibleItems.indexOf(item)
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs)) {
        GiftCard(
            title = item.title,
            imageUrl = item.imageUrl,
            priceText = link?.priceText,
            storeText = link?.store,
            countryCode = link?.country,
            isFavorite = item.isFavorite,
            status = GiftActionReducer.badge(item, isParentView = state.isParent),
            onClick = { actions.onOpenItem(item) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
            if (state.canToggleFavorite(item)) {
                TextLinkButton(
                    stringResource(if (item.isFavorite) R.string.gifts_unfavorite else R.string.gifts_favorite),
                    { actions.onToggleFavorite(item) },
                )
            }
            if (state.canToggleOwned(item)) {
                TextLinkButton(
                    stringResource(if (item.owned) R.string.gifts_back_to_list else R.string.gifts_already_owned),
                    { actions.onToggleOwned(item) },
                )
            }
            if (state.canEdit(item)) TextLinkButton(stringResource(R.string.gifts_delete), { actions.onDelete(item) })
            if (state.canReorder && position > 0) {
                TextLinkButton(stringResource(R.string.gifts_move_up), { actions.onMove(position, position - 1) })
            }
            if (state.canReorder && position in 0 until state.visibleItems.lastIndex) {
                TextLinkButton(stringResource(R.string.gifts_move_down), { actions.onMove(position, position + 1) })
            }
        }
    }
}
